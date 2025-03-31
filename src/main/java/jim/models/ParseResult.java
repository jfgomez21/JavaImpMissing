package jim.models;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ParseResult extends Result {
	@JsonProperty("package")
	public final FileEntry pkg = new FileEntry();
	public final List<FileImportEntry> imports = new ArrayList<>();
	public final List<FileTypeEntry> types = new ArrayList<>(); 

	public int firstImportStatementLine;
	public int lastImportStatementLine;
}
