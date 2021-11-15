package step.resources;

import step.attachments.FileResolver;
import step.core.accessors.Accessor;
import step.core.dynamicbeans.DynamicValue;
import step.core.entities.DependencyTreeVisitorHook;
import step.core.entities.Entity;
import step.core.entities.EntityDependencyTreeVisitor.EntityTreeVisitorContext;
import step.core.entities.EntityManager;

public class ResourceEntity extends Entity<Resource, Accessor<Resource>> {

	private final FileResolver fileResolver;
	
	public ResourceEntity(Accessor<Resource> accessor, ResourceManager resourceManager, FileResolver fileResolver, EntityManager entityManager) {
		super(EntityManager.resources, accessor, Resource.class);
		this.fileResolver = fileResolver;
		entityManager.addDependencyTreeVisitorHook(new DependencyTreeVisitorHook() {
			
			@Override
			public void onVisitEntity(Object t, EntityTreeVisitorContext visitorContext) {
				if(visitorContext.isRecursive() && t instanceof Resource) {
					String revisionId = resourceManager.getResourceRevisionByResourceId(((Resource) t).getId().toString()).getId().toHexString();
					visitorContext.visitEntity(EntityManager.resourceRevisions, revisionId);
				}
			}
		});
	}

	@Override
	public String resolveAtomicReference(Object atomicReferene, EntityTreeVisitorContext visitorContext) {
		if(atomicReferene != null) {
			if(atomicReferene instanceof String) {
				return resolveResourceId(atomicReferene);
			} else if (atomicReferene instanceof DynamicValue<?>) {
				Object value = ((DynamicValue<?>) atomicReferene).get();
				return resolveResourceId(value);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	@Override
	public Object updateAtomicReference(Object atomicReference, String resolvedEntityId,
			EntityTreeVisitorContext visitorContext) {
		Object newValue = null;
		if (atomicReference != null) {
			if (atomicReference instanceof String) {
				newValue = newPathForResourceId(atomicReference, resolvedEntityId);
			} else if (atomicReference instanceof DynamicValue<?>) {
				String newPathForResourceId = newPathForResourceId(((DynamicValue<?>) atomicReference).get(),
						resolvedEntityId);
				if (newPathForResourceId != null) {
					newValue = new DynamicValue<>(newPathForResourceId);
				}
			}
		}
		return newValue;
	}

	private String resolveResourceId(Object valueToResolve) {
		if(valueToResolve instanceof String) {
			String path = (String) valueToResolve;
			if(fileResolver.isResource(path)) {
				return fileResolver.resolveResourceId(path);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}
	
	private String newPathForResourceId(Object valueToResolve, String newEntityId) {
		if(valueToResolve instanceof String) {
			String path = (String) valueToResolve;
			if(fileResolver.isResource(path)) {
				return fileResolver.createPathForResourceId(newEntityId);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

}
