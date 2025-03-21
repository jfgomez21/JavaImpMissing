package jim;

import java.io.IOException;

import com.fasterxml.jackson.jr.annotationsupport.JacksonAnnotationExtension;
import com.fasterxml.jackson.jr.ob.JSON;

public abstract class AbstractJimTest {
	protected JSON json = JSON.builder().register(JacksonAnnotationExtension.std).enable(JSON.Feature.PRETTY_PRINT_OUTPUT).build();

	public void println(Object obj){
		try{
			System.out.println(json.asString(obj));
		}
		catch(IOException ex){
			throw new RuntimeException("json error", ex);
		}
	}
}
