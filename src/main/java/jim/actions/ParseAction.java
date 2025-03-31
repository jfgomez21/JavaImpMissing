package jim.actions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

import java.nio.file.FileSystem;
import java.nio.file.Files;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Problem;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;

import jim.javaparser.ClassOrInterfaceTypeVisitor;

import jim.models.FileEntry;
import jim.models.FileTypeEntry;
import jim.models.ParseResult;
import jim.models.FileImportEntry;

public class ParseAction implements JimAction<ParseResult> {
	private FileSystem fileSystem;
	private Map<String, List<String>> classes;

	public ParseAction(FileSystem fileSystem, Map<String, List<String>> classes){
		this.fileSystem = fileSystem;	
		this.classes = classes;
	}

	private void setRange(FileEntry entry, Node node){
		Optional<Range> range = node.getRange();

		if(range.isPresent()){
			Range r = range.get();

			entry.position.line = r.begin.line;
			entry.position.column = r.begin.column;
		}
	}

	private Map<String, FileImportEntry> getImportStatements(CompilationUnit unit){
		Map<String, FileImportEntry> imports = new LinkedHashMap<>();

		for(ImportDeclaration declaration : unit.getImports()){
			String name = declaration.getName().asString();

			if(declaration.isAsterisk()){
				name = String.format("%s.*", name);
			}

			FileImportEntry entry = new FileImportEntry();
			entry.value = name;
			entry.isStatic = declaration.isStatic();

			setRange(entry, declaration);

			imports.put(declaration.getName().removeQualifier().asString(), entry);
		}

		return imports;
	}

	private FileImportEntry getFileImportEntry(Map<String, FileImportEntry> imports, String name){
		if(imports.containsKey(name)){
			return imports.get(name);
		}

		int index = -1;

		while((index = name.lastIndexOf(".")) > -1){
			name = name.substring(0, index);

			if(imports.containsKey(name)){
				return imports.get(name);
			}
		}

		return null;
	}

	private boolean isFullyQualifiedClassName(Map<String, List<String>> classes, String name){
		int index = name.lastIndexOf(".");

		if(index > -1){
			String nm = name.substring(index + 1, name.length());
			List<String> choices = classes.get(nm);

			if(choices != null){
				return choices.contains(name);
			}
		}

		return false;
	}

	private boolean isInPackage(String packageName, String className){
		if(packageName == null){
			return false;
		}

		return className.startsWith(packageName);
	}

	private boolean hasClassName(List<String> choices, String packageName, String className){
		return choices.contains(String.format("%s.%s", packageName, className));
	}

	private void removeUnusedImports(Map<String, FileImportEntry> imports){
		Iterator<FileImportEntry> it = imports.values().iterator();

		while(it.hasNext()){
			FileImportEntry entry = it.next();

			if(!entry.isUsed && !entry.isStatic){
				it.remove();
			}
		}
	}

	private ParseResult parse(ParseResult result, Reader reader) throws IOException {
		FileEntry packageInfo = new FileEntry();

		CompilationUnit unit = StaticJavaParser.parse(reader);
		Optional<PackageDeclaration> pkg = unit.getPackageDeclaration();

		if(pkg.isPresent()){
			PackageDeclaration declaration = pkg.get();

			packageInfo.value = declaration.getName().asString();

			setRange(packageInfo, declaration);	
		}

		Map<String, FileImportEntry> imports = getImportStatements(unit);	
		int firstImportLine = Integer.MAX_VALUE;
		int lastImportLine = Integer.MIN_VALUE;

		for(FileImportEntry entry : imports.values()){
			int lineNum = entry.position.line;

			if(lineNum < firstImportLine){
				firstImportLine = lineNum;
			}
			
			if(lineNum > lastImportLine){
				lastImportLine = lineNum;
			}
		};

		if(firstImportLine == Integer.MAX_VALUE){
			firstImportLine = 0;
		}

		if(lastImportLine == Integer.MIN_VALUE){
			lastImportLine = 0;
		}

		Map<String, FileTypeEntry> types = new HashMap<>();

		unit.accept(new ClassOrInterfaceTypeVisitor(), types);

		Iterator<FileTypeEntry> it = types.values().iterator();

		while(it.hasNext()){
			FileTypeEntry entry = it.next();
			FileImportEntry imprt = getFileImportEntry(imports, entry.value);

			if(imprt == null){
				if(!isFullyQualifiedClassName(classes, entry.value)){
					List<String> choices = classes.get(entry.value);

					if(choices != null){
						int count = choices.size();

						if(count == 1){
							String choice = choices.get(0);
							
							if(!isInPackage("java.lang", choice) && !isInPackage(packageInfo.value, choice)){
								FileImportEntry newImport = new FileImportEntry();
								newImport.value = choice;
								newImport.isUsed = true;

								imports.put(entry.value, newImport);
							}
							
							it.remove();
						}
						else{
							if(hasClassName(choices, "java.lang", entry.value) || hasClassName(choices, packageInfo.value, entry.value)){
								it.remove();	
							}
							else{
								entry.choices.addAll(choices);
							}
						}
					}
				}	
				else{
					it.remove();
				}
			}
			else{
				imprt.isUsed = true;
				it.remove();
			}
		}

		removeUnusedImports(imports);

		result.pkg.value = packageInfo.value;
		result.pkg.position.line = packageInfo.position.line;
		result.pkg.position.column = packageInfo.position.column;

		result.imports.addAll(imports.values());
		result.types.addAll(types.values());

		result.firstImportStatementLine = firstImportLine;
		result.lastImportStatementLine = lastImportLine;

		Collections.sort(result.types, (t1, t2) -> {
			int value = t1.position.line - t2.position.line;

			if(value == 0){
				return t1.position.column - t2.position.column;
			}	

			return value;
		});

		return result;
	}

	private void addParseProblem(ParseResult result, ParseProblemException ex){
		List<Problem> problems = ex.getProblems();
		String message = "Parse error";

		if(!problems.isEmpty()){
			Optional<TokenRange> opt = problems.get(0).getLocation();

			if(opt.isPresent()){
				Optional<Range> optr = opt.get().toRange();

				if(optr.isPresent()){
					Range range = optr.get();

					message = String.format("Parse error - line %d column %d", range.begin.line, range.begin.column);
				}
			}
		}

		result.errorMessages.add(message);
	}

	//TODO - log exceptions
	public ParseResult parse(String filename){
		ParseResult result = new ParseResult();

		try(Reader reader = Files.newBufferedReader(fileSystem.getPath(filename))){
			parse(result, reader);
		}
		catch(IOException ex){
			result.errorMessages.add(String.format("unable to read file - %s", filename));
		}
		catch(ParseProblemException ex){
			addParseProblem(result, ex);
		}

		return result;
	}

	//TODO - log exceptions
	public ParseResult parse(InputStream input){
		ParseResult result = new ParseResult();

		try(Reader reader = new BufferedReader(new InputStreamReader(input))){
			parse(result, reader);
		}
		catch(IOException ex){
			result.errorMessages.add(String.format("unable to read from input stream - %s", ex.getMessage()));
		}
		catch(ParseProblemException ex){
			addParseProblem(result, ex);
		}

		return result;
	}

	public ParseResult parseJavaSource(String source){
		ParseResult result = new ParseResult();

		try(Reader reader = new StringReader(source)){
			parse(result, reader);
		}
		catch(IOException ex){
			result.errorMessages.add(String.format("unable to read from input string - %s", ex.getMessage()));
		}
		catch(ParseProblemException ex){
			addParseProblem(result, ex);
		}

		return result;
	}
}
