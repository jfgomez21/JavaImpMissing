package jim.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FileImportEntry extends FileEntry {
	@JsonProperty("static") 
	public boolean isStatic;

	@JsonIgnore
	public boolean isUsed;

}
