package jim.io;

import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

public class ClassListDeserializer {
	private FileSystem fileSystem;

	public ClassListDeserializer(FileSystem fileSystem){
		this.fileSystem = fileSystem;
	}

	public Map<String, List<String>> deserialize(InputStream input) throws IOException {
		List<String> lines = IOUtils.readLines(input, StandardCharsets.UTF_8);
		Map<String, List<String>> dest = new HashMap<>();

		for(String line : lines){
			String[] values = line.split(" ");

			List<String> classes = dest.get(values[0]);

			if(classes == null){
				classes = new ArrayList<>();

				dest.put(values[0], classes);
			}

			for(int i = 1; i < values.length; i++){
				classes.add(values[i]);
			}
		}	

		return dest;
	}

	public Map<String, List<String>> deserialize(String filename, boolean required) throws IOException {
		Path path = fileSystem.getPath(filename);

		if(!Files.exists(path)){
			if(required){
				throw new IOException(String.format("file not found - %s", filename));
			}

			return new HashMap<>();
		}

		try(InputStream input = Files.newInputStream(path)){
			return deserialize(input);
		}
	}
}	
