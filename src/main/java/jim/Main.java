package jim;

import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.jr.annotationsupport.JacksonAnnotationExtension;
import com.fasterxml.jackson.jr.ob.JSON;

import jim.actions.ParseAction;

import jim.io.ClassListDeserializer;

import jim.models.ParseResult;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class Main {
	private static final List<String> COMMANDS = Arrays.asList("generate", "parse", "import");

	private static void printUsage(String filename, int exitCode) throws IOException {
		try(InputStream input = Main.class.getClassLoader().getResourceAsStream(filename)){
			System.out.println(IOUtils.toString(input, StandardCharsets.UTF_8));
		}

		System.exit(exitCode);
	}

	private static List<?> parseCommandArguments(String[] args) throws IOException {
		OptionParser parser = new OptionParser("h");
		parser.accepts("help");

		OptionSet options = parser.parse(args);
		List<?> nonOptions = options.nonOptionArguments();

		if(options.has("h") || options.has("help")){
			printUsage("usage.txt", 0);
		}

		if(nonOptions.isEmpty() || !COMMANDS.contains(nonOptions.get(0).toString())){
			printUsage("usage.txt", -1);
		}

		return nonOptions;
	}

	private static JSON createJSON(boolean prettyPrint){
		JSON.Builder builder = JSON.builder().register(JacksonAnnotationExtension.std);
		
		if(prettyPrint){
			builder.enable(JSON.Feature.PRETTY_PRINT_OUTPUT);
		}

		return builder.build();
	}

	//TODO - handle file not found for class file
	private static Map<String, List<String>> parseClassFile(FileSystem fileSystem, String filename, boolean required){
		try{
			return new ClassListDeserializer(fileSystem).deserialize(filename, required);
		}
		catch(IOException ex){
			System.err.println(ex.getMessage());
			System.exit(-1);
		}

		return null;
	}

	private static void parseAction(String[] args) throws IOException {
		OptionParser parser = new OptionParser("hp");
		parser.accepts("help");
		parser.accepts("pretty-print");
		parser.accepts("class-file").withRequiredArg();
		parser.accepts("choice-file").withRequiredArg();

		OptionSet options = parser.parse(args);
		List<?> nonOptions = options.nonOptionArguments();

		if(options.has("h") || options.has("help")){
			printUsage("usage-parse.txt", 0);
		}

		if(nonOptions.isEmpty()){
			printUsage("usage-parse.txt", -1);	
		}

		String classFileName = "java.jim";
		String choiceFileName = "choices.jim";

		if(options.has("class-file")){
			classFileName = options.valueOf("class-file").toString();
		}

		if(options.has("choice-file")){
			choiceFileName = options.valueOf("choice-file").toString(); 
		}

		FileSystem fileSystem = FileSystems.getDefault();
		JSON json = createJSON(options.has("p") | options.has("pretty-print"));

		Map<String, List<String>> classes = parseClassFile(fileSystem, classFileName, options.has("class-file"));

		ParseResult result = new ParseAction(fileSystem, classes).parse(nonOptions.get(0).toString());	

		System.out.println(json.asString(result));
	}

	//TODO - parse arguments
	public static void main(String[] args) throws IOException {
		String command = null;
		String[] parameters = null;

		if(!COMMANDS.contains(args[0])){
			List<?> arguments = parseCommandArguments(args);
			int count = arguments.size();

			command = arguments.get(0).toString();

			parameters = arguments.subList(1, count).toArray(new String[count - 1]);
		}
		else{
			command = args[0];
			parameters = Arrays.copyOfRange(args, 1, args.length);
		}

		if("parse".equals(command.toString())){
			parseAction(parameters);
		}
	}
}
