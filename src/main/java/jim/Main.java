package jim;

import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

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
	private static void printUsage(String filename, int exitCode) throws IOException {
		try(InputStream input = Main.class.getClassLoader().getResourceAsStream(filename)){
			System.out.println(IOUtils.toString(input, StandardCharsets.UTF_8));
		}

		System.exit(exitCode);
	}

	private static JSON createJSON(boolean prettyPrint){
		JSON.Builder builder = JSON.builder().register(JacksonAnnotationExtension.std);
		
		if(prettyPrint){
			builder.enable(JSON.Feature.PRETTY_PRINT_OUTPUT);
		}

		return builder.build();
	}

	private static Map<String, List<String>> parseClassFile(ClassListDeserializer deserializer, String filename, boolean required){
		try{
			return deserializer.deserialize(filename, required);
		}
		catch(IOException ex){
			System.err.println(ex.getMessage());
			System.exit(-1);
		}

		return Map.<String, List<String>>of();
	}

	public static void main(String[] args) throws IOException {
		OptionParser parser = new OptionParser("hp");
		parser.accepts("help");
		parser.accepts("pretty-print");
		parser.accepts("class-file").withRequiredArg();
		parser.accepts("choice-file").withRequiredArg();

		OptionSet options = parser.parse(args);
		List<?> nonOptions = options.nonOptionArguments();

		if(options.has("h") || options.has("help")){
			printUsage("usage.txt", 0);
		}

		if(nonOptions.isEmpty()){
			printUsage("usage.txt", -1);	
		}

		String classFileName = "classes.jim";
		String choiceFileName = "choices.jim";

		if(options.has("class-file")){
			classFileName = options.valueOf("class-file").toString();
		}

		if(options.has("choice-file")){
			choiceFileName = options.valueOf("choice-file").toString(); 
		}

		FileSystem fileSystem = FileSystems.getDefault();
		ClassListDeserializer deserializer = new ClassListDeserializer(fileSystem);
		JSON json = createJSON(options.has("p") | options.has("pretty-print"));

		Map<String, List<String>> classes = parseClassFile(deserializer, classFileName, options.has("class-file"));
		Map<String, List<String>> choices = parseClassFile(deserializer, choiceFileName, options.has("choice-file"));

		ParseResult result = new ParseAction(fileSystem, classes, choices).parse(nonOptions.get(0).toString());	

		System.out.println(json.asString(result));
	}
}
