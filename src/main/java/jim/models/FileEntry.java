package jim.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FileEntry {
	public final FilePosition position = new FilePosition();
	public String value;
}
