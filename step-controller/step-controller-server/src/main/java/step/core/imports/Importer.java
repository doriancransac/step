package step.core.imports;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import step.core.GlobalContext;
import step.core.Version;
import step.core.objectenricher.ObjectEnricher;

public interface Importer {
	
	public void init(GlobalContext context, String entityType);
	
	public void importOne(JsonParser jParser, ObjectMapper mapper, ObjectEnricher objectEnricher, Version version)  throws JsonParseException, JsonMappingException, IOException;
	
	public void importMany(File file, ObjectMapper mapper, ObjectEnricher objectEnricher, Version version) throws IOException; 
}
