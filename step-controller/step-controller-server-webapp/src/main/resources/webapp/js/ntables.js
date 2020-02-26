/*******************************************************************************
 * (C) Copyright 2016 Jerome Comte and Dorian Cransac
 *  
 * This file is part of STEP
 *  
 * STEP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * STEP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with STEP.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
angular.module('tables', ['export','dataTable'])

.controller('StTableController', function ($scope) {
	var ctrl = this;

	var serverSide = $scope.collection?true:false;

	var scopesTracker = new ScopeTracker();
	var headerScopesTracker = new ScopeTracker();

	$scope.$on('$destroy', function() {
		scopesTracker.destroy();
		headerScopesTracker.destroy();
	});

	ctrl.dtColumns = {}
	
	ctrl.getDtColumns = function() {
	  var result = [];
	  _.each(_.keys(ctrl.dtColumns).sort(), function(key) {
	    _.each(ctrl.dtColumns[key], function(value) {
	      result.push(value);
	    })
	  })
	  return result;
	}

	ctrl.addColumn = function(column, position) {
		var colDef = {};

		if (column.name) {
			colDef['data'] = column.name;
			colDef['name'] = column.name;
		} else {
			colDef['defaultContent'] = "";
		}
		
		if(column.width) {
		  colDef['width'] = column.width;
		}

		colDef.render = function ( data, type, row, meta ) {
			if(type==='filter') {
				// get the HTML content of the cell after it has been rendered (digested) by angular
				var htmlContent = $($scope.table.cell(meta.row, meta.col).node()).text()
				// return the HTML content after rendering as base for the column searches (type='filter')
				return htmlContent
			} else {
				return data
			}
		}

		colDef.createdCell = function(td, cellData, rowData, row, col) {
			var rowScope;
			var content = column.cellTransclude(function(clone, scope) {
				if(serverSide) {
					scope.row = JSON.parse(rowData[0]);
				} else {
					scope.row = rowData;
				}
				scope.tableScope = $scope;
				rowScope = scope;
			});

			angular.element(td).empty();
			angular.element(td).append(content);
			if(serverSide) {
				rowScope.$digest();
			}
			scopesTracker.track(rowScope);
		};

		colDef.headerRenderer = createHeaderRenderer(column.headerTransclude);
		colDef.secondHeaderRenderer = createHeaderRenderer(column.secondHeaderTransclude);

		function createHeaderRenderer(headerTransclude) {
			return function(element, column, handle) {
				var headerScope;
				var content = headerTransclude(function(clone, scope){
					headerScope = scope;
					headerScope.initialValue = column.search();
					headerScope.search = function(expression){handle.search(colDef.name,expression)};
				});
				element.empty();
				element.append(content);
				if(headerScope) {
					headerScopesTracker.track(headerScope);
				}
			}
		}
		
		var slot = ctrl.dtColumns[position]
		if(!slot) {
		  slot = [];
		  ctrl.dtColumns[position] = slot;
		}
		slot.push(colDef);
	}
	
  ctrl.newCycle = function() {
    scopesTracker.newCycle()
  }
})
.directive('stTable', function($compile, $http, Preferences, stateStorage, $timeout) {
	return {
		scope : {
			uid: '=',
			handle: '=?',
			data: '=?',
			collection: '=?',
			filter: '=?',
			dom: '=?',
			order: '=?',
			persistState: '=?',
			// set to true if the table should allow multiple selection
			multipleSelection: '=?',
			// per default no row is selected. The default selection can
			// be defined by this parameter. Accepted value are: 'all', 'none', or a function 
			// as default selector
			defaultSelection: '=?',
			// the field to be used as ID to track selection
			selectionAttribute: '=?',
			// a function that shoud be called when an element is selected. This function is 
			// called only when multipleSelection = false
			onSelection: '=?'
		},
		transclude : {
			'stActions' : '?stActions',
			'stColumns' : '?stColumns'
		},
		replace: false,    
		controller : 'StTableController',
		controllerAs: 'table',
		link : function(scope, element, attr, controller, transclude) {
		  var serverSide = scope.collection?true:false;

		  var tableElement = angular.element(element).find('table');

		  scope.selectionModel = new SelectionModel(function(){
		    if(serverSide) {
		      return _.map(scope.table.rows?scope.table.rows().data():[], function(row) {
		        return JSON.parse(row[0]);
		      })
		    } else {
		      return scope.data
		    }
		  }, scope.selectionAttribute);

		  var defaultSelection = scope.defaultSelection;
		  if(defaultSelection) {
        if(_.isFunction(defaultSelection)) {
          scope.selectionModel.setDefaultSelector(defaultSelection);
        } else if (defaultSelection=='all') {
          scope.selectionModel.setDefaultSelection(true);
        } else {
          scope.selectionModel.setDefaultSelection(false);
        }
      } else {
        scope.selectionModel.setDefaultSelection(false);
      }
		  
      function getFilteredData() {
        return scope.table.rows({"filter":"applied"}).data();
      }
      
      function isTableFiltered() {
        var hasFilter = false;
        scope.table.columns().eq(0).each(function(index) {
          var col = scope.table.column(index);
          if(!hasFilter && col.search().length>0) {
            hasFilter = true;
          }
        });
        return hasFilter;
      }
      
      function sendSelectionChangeEvent() {
        if(scope.onSelectionChange) {
          scope.onSelectionChange();        
        }
      }

      scope.setSelectionOnFilteredRows = function(value) {
        if(!isTableFiltered()) {
          scope.selectionModel.setDefaultSelection(value);
          scope.selectionModel.setSelectionAll(value);
        } else {
          scope.selectionModel.setSelectionAll(false);
          _.each(getFilteredData(),function(dataRow){
            scope.selectionModel.setSelectionForObject(dataRow,value);
          })
        }
        sendSelectionChangeEvent();
      };
      
      controller.multipleSelection = scope.multipleSelection; 
      controller.select = function(item) {
        if(scope.onSelection) {
          if(scope.selectionAttribute) {
            var selectedId = new Bean(item).getProperty(scope.selectionAttribute);
            scope.onSelection(selectedId);        
          }
        }
      }
      
		  controller.selectionModelByInput = function(bean) {
		    return function (value) {
		      if (angular.isDefined(value)) {
		        scope.selectionModel.setSelectionForObject(bean, value);
		        sendSelectionChangeEvent();
		      } else {
		        return scope.selectionModel.isObjectSelected(bean);
		      }
		    }
		  }
		  
		  function loadTableData() {
		    var value = scope.data;
		    if(scope.table) {
          scope.table.clear();
          if (value && value.length > 0) {
            scope.table.rows.add(value);
            // perform the table draw after the current angular digest cycle in order to let angular render all the cells  (See comment in colDef.render above) 
            $timeout(function() {
              scope.table.draw(false)
            })
          }
        }
		  }
		  
		  controller.reload = function() {
		    var columns = controller.getDtColumns();
		    if(columns && columns.length>0) {
		      // First destroy the previous table if any
	        if(scope.table && scope.table.destroy) {
	          scope.table.destroy()
	          // remove the headers added "manually" (see below)
	          tableElement.empty();
	        }
	        
	        // Build the table options
	        var tableOptions = {}
	        tableOptions.pageLength = parseInt(Preferences.get("tables_itemsperpage", 10));
	        tableOptions.dom = scope.dom?scope.dom:'lrtip';
	        // disable autoWidth: the auto sizing of column widths seems to work better when calculated by the browser
	        tableOptions.autoWidth = false;
	        tableOptions.fnDrawCallback = function() {
	          controller.newCycle();
	        };
	        tableOptions.columns = columns;
	        if(scope.order) {
	          tableOptions.order = scope.order;
	        }

	        if (scope.persistState == null || scope.persistState) {
	          if (scope.uid) {
	            tableOptions.stateSave = true;
	            tableOptions.stateSaveCallback = function(settings, data) {
	              // Append the number of columns to the id as the method controller.reload() might be called several times during table building 
	              var uid = scope.uid + tableOptions.columns.length;
	              var state = stateStorage.get(scope, uid);
	              if (!state) {
	                state = {};
	              }
	              state.tableState = data;
	              
	              stateStorage.store(scope, state, uid);
	            };
	            tableOptions.stateLoadCallback = function(settings) {
	              // Append the number of columns to the id as the method controller.reload() might be called several times during table building 
	              var uid = scope.uid + tableOptions.columns.length;
	              var state = stateStorage.get(scope, uid);
	              return (state && state.tableState) ? state.tableState : null;
	            }
	          } else {
	            console.error("Unable to persist table state if the table uid isn't specified. Please set the attribute 'uid'")
	          }
	        }

	        if(serverSide) {
	          var query = 'rest/table/' + scope.collection + '/data';
	          if(scope.filter) {
	            query += '?filter=' + encodeURIComponent(scope.filter);
	          }
	          tableOptions.ajax = {
	              'url' : query,
	              'type' : 'POST'
	          }

	          tableOptions.processing = false;
	          tableOptions.serverSide = true;
	          tableOptions.sProcessing = '';
	        }

	        // Initialize the DataTable with the built options
	        var table = tableElement.DataTable(tableOptions);
	        scope.table = table;

	        // Table actions
	        var tableActions = transclude(function() {}, null, 'stActions');
	        var cmdDiv;
	        if (element.find('div.dataTables_filter').length > 0) {
	          cmdDiv = element.find('div.dataTables_filter');
	          cmdDiv.parent().removeClass('col-sm-6').addClass('col-sm-9');
	          element.find('div.dataTables_length').parent().removeClass('col-sm-6').addClass('col-sm-3');
	        } else {
	          cmdDiv = element.find('div.dataTables_length');
	        }
	        angular.element('<div class="pull-right"></div>').append(tableActions).appendTo(cmdDiv);

	        if(!scope.handle) {
	          scope.handle = {};
	        }
	        scope.handle.reload = function() {
	          table.ajax.reload(null, false);
	        }
	        scope.handle.search = function(columnName, searchExpression) {
	          var column = table.column(columnName+':name');
	          column.search(searchExpression,true,false).draw();
	        }
	        scope.handle.getRows = scope.selectionModel.getDataRowsBySelection.bind(scope.selectionModel);
	        scope.handle.getSelectedIds = scope.selectionModel.getSelectedIds.bind(scope.selectionModel);
	        scope.handle.getSelectionMode = scope.selectionModel.getSelectionMode.bind(scope.selectionModel);

	        // render first header
	        table.columns().indexes().flatten().each(function(i) {
	          table.settings()[0].aoColumns[i].headerRenderer(angular.element(table.column(i).header()),table.column(i),scope.handle);
	        })

	        // render second header
	        tableElement.find('thead').append('<tr class="searchheader"/>');
	        $('th',tableElement.find('thead tr[role="row"]').eq(0)).css({ 'border-bottom': '0' }).each( function (colIdx) {
	          tableElement.find('thead tr.searchheader').append('<th style="border-top:0" />' );
	        });
	        table.columns().indexes().flatten().each(function(i) {
	          var thIdx = $('th',tableElement.find('thead tr[role="row"]')).index(table.column(i).header());
	          if(thIdx>=0) {
	            var secondHeader = $('th',tableElement.find('thead tr.searchheader')).eq(thIdx);
	            table.settings()[0].aoColumns[i].secondHeaderRenderer(secondHeader,table.column(i),scope.handle);
	          }
	        });
	        
	        loadTableData();
		    }
		    
		  }
		  
		  if(!serverSide) {
		    // Listen to changes in the data collection
        scope.$watchCollection('data', function(value) {
          loadTableData();
        }) 
      }
		  
      scope.$on('$destroy', function() {
        if(scope.table) {
          scope.table.destroy();
        }
      });
		  
		  controller.reload();
		},
		templateUrl : 'partials/ntable.html'
	};
})

.directive('stColumn', function($compile, $http, Preferences, stateStorage) {
	return {
		replace: true,
		transclude : {
			'header' : '?header',
			'secondHeader' : '?secondHeader',
			'cell' : '?cell',
		},
		require: '^stTable',
		scope : {
			'name':'@?',
			'width':'=?'
		},
		controller : function($scope) {
		},
		link : function(scope, elm, attrs, tableController, transclude) {
			// Get the position of this column in the closest st-columns parent
		  var parentsUntilStColumns = elm.parentsUntil("st-columns");
		  var elementInStColumns = elm.parentsUntil("st-columns")[parentsUntilStColumns.length-1]
		  if(!elementInStColumns) {
		    elementInStColumns = elm;
		  } else {
		    elementInStColumns = $(elementInStColumns)
		  }
      var positionInParent = elementInStColumns.parent().children().index(elementInStColumns)
			tableController.addColumn({
				name:scope.name,
				width:scope.width,
				headerTransclude : function(callback) {
					return transclude(callback, null, 'header')
				},
				secondHeaderTransclude : function(callback) {
					return transclude(callback, null, 'secondHeader')
				},
				cellTransclude : function(callback) {
					return transclude(callback, null, 'cell')
				},
			}, positionInParent)
			if(tableController.reload) {
			  tableController.reload()
			}

		}
	}
})

.directive('stSelectionColumn', function() {
  return {
    restrict: 'E',
    scope: {},
    require: '^stTable',
    controller: function($scope) {
    },
    link : function(scope, element, attrs, tableController, transclude) {
      scope.selectionModelByInput = tableController.selectionModelByInput;
      scope.select = tableController.select;
      scope.multipleSelection = tableController.multipleSelection;
    },
    templateUrl: 'partials/table/selectionColumn.html'}
})

.directive('stSelectionActions', function() {
  return {
    restrict: 'E',
    scope: {},
    controller: function($scope) {
    },
    link : function(scope, element, attrs, tableController, transclude) {
      scope.selectAll = function() {
        scope.$parent.$parent.setSelectionOnFilteredRows(true);
      } 
      
      scope.unselectAll = function() {
        scope.$parent.$parent.setSelectionOnFilteredRows(false);
      }     
    },
    templateUrl: 'partials/table/selectionActions.html'}
})

//hack to suppress DataTable warning
//see http://stackoverflow.com/questions/11941876/correctly-suppressing-warnings-in-datatables
window.alert = (function() {
	var nativeAlert = window.alert;
	return function(message) {
		if(message){
			message.toString().indexOf("DataTables warning") === 0 ?
					console.warn(message) :
						nativeAlert(message);
		}else{
			nativeAlert(message);
		}
	}
})();