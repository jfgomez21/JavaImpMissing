package jim.actions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import jim.AbstractJimTest;

import jim.models.FileTypeEntry;
import jim.models.ParseResult;

import static org.junit.Assert.*;

//TODO - test multiple choices
//TODO - test unused imports removal
public class TestParseAction extends AbstractJimTest {
	@Test
	public void testParseJavaSourceWithObjectCreation() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"ArrayList", Arrays.asList("java.util.ArrayList"),
			"String", Arrays.asList("java.lang.String")
		);
		String java = "public class Test { public static void main(String[] args){ new ArrayList(); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals("java.util.ArrayList", result.imports.get(0).value);
	}

	@Test
	public void testParseJavaSourceWithJavaLangPackageExclusion() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"String", Arrays.asList("java.lang.String")
		);
		String java = "public class Test { public static void main(String[] args){  }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithGenericTypes() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"String", Arrays.asList("java.lang.String"),
			"List", Arrays.asList("java.util.List"),
			"MyObject", Arrays.asList("dummy.MyObject"),
			"Map", Arrays.asList("java.util.Map")
		);
		String java = "public class Test { public static void main(String[] args){ Map<String, List<MyObject>> list; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(3, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.util.Map")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.util.List")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("dummy.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithStaticClasses() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"FileSystems", Arrays.asList("java.nio.file.FileSystems")
		);
		String java = "public class Test { public void dummy(){ var f = FileSystems.getDefault(); f.toString(); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.nio.file.FileSystems")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithClassExpressions() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"Test", Arrays.asList("abc.Test")		
		);
		String java = "package abc; public class Test { public void dummy(){ Test.class.getClassLoader().getResourceAsStream(\"abc\"); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithMarkerAnnotations() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"Test", Arrays.asList("junit.Test")		
		);
		String java = "public class Dummy { @Test public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.types.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("junit.Test")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithMarkerAnnotationsWithPackageName() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"Test", Arrays.asList("junit.Test")		
		);
		String java = "public class Dummy { @junit.Test public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.types.isEmpty());
		assertEquals(true, result.imports.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithSingleMemberAnnotations() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"Service", Arrays.asList("org.abc.Service"),	
			"MyClass", Arrays.asList("com.def.MyClass")
		);
		String java = "@Service(MyClass.BEAN_NAME) public class Dummy { public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.types.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("org.abc.Service")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("com.def.MyClass")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithSingleMemberAnnotationWithClass() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"Service", Arrays.asList("org.abc.Service"),	
			"MyClass", Arrays.asList("com.def.MyClass")
		);
		String java = "@Service(MyClass.class) public class Dummy { public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.types.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("org.abc.Service")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("com.def.MyClass")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithNormalAnnotations() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"Test", Arrays.asList("junit.Test"),	
			"IOException", Arrays.asList("java.io.IOException"),	
			"MyClass", Arrays.asList("com.abc.MyClass")	
		);
		String java = "public class Dummy { @Test(expected = IOException.class, value = MyClass.VALUE) public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.types.isEmpty());
		assertEquals(3, result.imports.size());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("junit.Test")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.IOException")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("com.abc.MyClass")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithTypeParameter() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of();
		String java = "public class Dummy { public <T> void dummy(T ... values){  }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithFullyQualifiedNameMethodCall() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of();
		String java = "public class Dummy { public void dummy(){ java.nio.file.FileSystems.getDefault();  }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithFullyQualifiedName() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"String", Arrays.asList("java.lang.String"),
			"List", Arrays.asList("java.util.List"),
			"ArrayList", Arrays.asList("java.util.ArrayList")
		);
		String java = "public class Dummy { public void dummy(){ java.util.List<String> l = new java.util.ArrayList<>();  }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithNestedClass() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"Map", Arrays.asList("java.util.Map")
		);
		String java = "import java.util.Map; public class Dummy { public void dummy(){ Map.Entry e = null;  }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithFullyQualifiedNameNoMatch() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"String", Arrays.asList("java.lang.String")
		);
		String java = "public class Dummy { public void dummy(){ java.util.List<String> l = new java.util.ArrayList<>();  }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(2, result.types.size());

		assertEquals(true, result.types.stream().filter(e -> e.value.equals("java.util.List")).findFirst().isPresent());
		assertEquals(true, result.types.stream().filter(e -> e.value.equals("java.util.ArrayList")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithStaticClassField() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"JSON", Arrays.asList("jackson.JSON"),
			"JacksonAnnotationExtension", Arrays.asList("jackson.JacksonAnnotationExtension")
		);
		String java = "public class Dummy { " +
			"protected JSON json = JSON.builder().register(JacksonAnnotationExtension.std).enable(JSON.Feature.PRETTY_PRINT_OUTPUT).build();" +
			"public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("jackson.JSON")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("jackson.JacksonAnnotationExtension")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithObjectField() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"System", Arrays.asList("java.lang.System"),
			"Object", Arrays.asList("java.lang.Object")
		);
		String java = "public class Dummy { public void dummy(){ Object obj; System.out.prinln(obj.value);  }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithMethodArguments() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"System", Arrays.asList("java.lang.System"),
			"IOUtils", Arrays.asList("org.apache.commons.io.IOUtils"),
			"StandardCharsets", Arrays.asList("java.nio.charset.StandardCharsets")
		);
		String java = "public class Dummy { public void dummy(){ System.out.println(IOUtils.toString(input, StandardCharsets.UTF_8)); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("org.apache.commons.io.IOUtils")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.nio.charset.StandardCharsets")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithObjectCreationAndScope() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"ParseAction", Arrays.asList("jim.actions.ParseAction")
		);
		String java = "public class Dummy { public void dummy(){ new ParseAction(fileSystem, classes).parse(nonOptions.get(0).toString()); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("jim.actions.ParseAction")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithPackageAlreadyPresent() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"JsonIgnore", Arrays.asList("com.fasterxml.jackson.annotation.JsonIgnore")
		);
		String java = "package def;\n\n import com.fasterxml.jackson.annotation.JsonIgnore;\n\n public class Dummy { @JsonIgnore public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("com.fasterxml.jackson.annotation.JsonIgnore")).findFirst().isPresent());

		assertEquals(3, result.firstImportStatementLine);
		assertEquals(3, result.lastImportStatementLine);
	}

	@Test
	public void testParseJavaSourceWithObjectCreationMethodArgument() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ obj.someMethod(new MyObject(), 1); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithTryStatementResources() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"Reader", Arrays.asList("java.io.Reader"),
			"BufferedReader", Arrays.asList("java.io.BufferedReader"),
			"InputStreamReader", Arrays.asList("java.io.InputStreamReader"),
			"System", Arrays.asList("java.lang.System"),
			"Exception", Arrays.asList("java.lang.Exception")
		);
		String java = "public class Dummy { public void dummy(){ try(Reader reader = new BufferedReader(new InputStreamReader(System.in))){ } catch(Exception ex) { } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(3, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.Reader")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.BufferedReader")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.InputStreamReader")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithTryStatement() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"IOUtils", Arrays.asList("org.apache.commons.io.IOUtils"),
			"StandardCharsets", Arrays.asList("java.nio.StandardCharsets"),
			"System", Arrays.asList("java.lang.System"),
			"Exception", Arrays.asList("java.lang.Exception")
		);
		String java = "public class Dummy { public void dummy(){ try{ System.out.println(IOUtils.toString(input, StandardCharsets.UTF_8)); } catch(Exception ex) { } }}";
			

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("org.apache.commons.io.IOUtils")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.nio.StandardCharsets")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithTryStatementAndVariableDeclaration() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject"),
			"RuntimeException", Arrays.asList("java.lang.RuntimeException")
		);
		String java = "public class Dummy { public void dummy(){ try{ MyObject obj = null; } catch(RuntimeException ex) { } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithTryStatementAndReturnExpression() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"IOUtils", Arrays.asList("org.apache.commons.io.IOUtils"),
			"StandardCharsets", Arrays.asList("java.nio.StandardCharsets"),
			"System", Arrays.asList("java.lang.System"),
			"Exception", Arrays.asList("java.lang.Exception")
		);
		String java = "public class Dummy { public void dummy(){ try{ return IOUtils.toString(input, StandardCharsets.UTF_8); } catch(Exception ex) { } }}";
			

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("org.apache.commons.io.IOUtils")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.nio.StandardCharsets")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithCatchClause() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"IOException", Arrays.asList("java.io.IOException")
		);
		String java = "public class Dummy { public void dummy(){ try{  } catch(IOException ex) { } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.IOException")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithCatchClauseUnion() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"IOException", Arrays.asList("java.io.IOException"),
			"ParseException", Arrays.asList("java.text.ParseException")
		);
		String java = "public class Dummy { public void dummy(){ try{  } catch(IOException | ParseException  ex) { } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.IOException")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.text.ParseException")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithFinallyBlock() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ try{  } finally { MyObject obj = null; } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithDefaultToJavaLangPackageIfMultpleChoicesExist() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"String", Arrays.asList("java.lang.String", "abc.String", "def.String")
		);
		String java = "public class Dummy { public void dummy(){ String str; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithDefaultToCurrentPackageIfMultpleChoicesExist() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"String", Arrays.asList("abc.String", "def.String", "my.data.String")
		);
		String java = "package my.data; public class Dummy { public void dummy(){ String str; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}	

	@Test
	public void testParseJavaSourceWithClassFields() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { private MyObject obj;  public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithFieldAccessMethodCall() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of();
		String java = "public class Dummy { public void dummy(){ obj.imports.get(0).value; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}
	
	@Test
	public void testParseJavaSourceWithSingleMemberAnnotationWithArrayInitalizer() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"Service", Arrays.asList("org.abc.Service"),	
			"MyClass", Arrays.asList("com.def.MyClass")
		);
		String java = "public class Dummy { @Service({MyClass.class}) public void dummy(){ }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.types.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("org.abc.Service")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("com.def.MyClass")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithForEachStatement() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject"),
			"File", Arrays.asList("java.io.File")
		);
		String java = "public class Dummy { public void dummy(){ for(MyObject obj : objs){ File f = null; } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.File")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithForStatement() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ for(int i = 0; i < 1; i++){ MyObject obj = null; } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithLambdaExpressionBody() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject"),
			"String", Arrays.asList("abc.String"),
			"File", Arrays.asList("java.io.File")
		);
		String java = "public class Dummy { public void dummy(){ values.forEach((MyObject<String> obj) -> { File f = null; }); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(3, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.String")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.io.File")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithLambdaExpression() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ values.forEach(obj -> obj.value = new MyObject()); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithCast() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject"),
			"Child", Arrays.asList("abc.Child")
		);
		String java = "public class Dummy { public void dummy(){ MyObject obj = (Child) value; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.Child")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithInstanceOf() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ boolean value = obj instanceof MyObject; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithInstanceOfAndIfStatement() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ if(obj instanceof MyObject){ } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithWhileStatement() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ while(true){ MyObject obj = null; } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithDoStatement() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ do { MyObject obj = null; } while(true); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithConstantValue() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { private final static int MY_VAR = 0;  public void dummy(){ obj.setValue(MY_VAR); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithParseError() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of();
		String java = "public class Dummy { public void dummy(){} ";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(1, result.errorMessages.size());
		assertEquals("Parse error - line 1 column 42", result.errorMessages.get(0));
		assertEquals(true, result.imports.isEmpty());
		assertEquals(true, result.types.isEmpty());
	}

	@Test
	public void testParseJavaSourceWithWildcardImportStatement() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"ArrayList", Arrays.asList("java.util.ArrayList"),
			"List", Arrays.asList("java.util.List"),
			"String", Arrays.asList("java.lang.String")
		);
		String java = "import java.util.*; public class Test { public static void main(String[] args){ List l = new ArrayList(); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(2, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.util.List")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("java.util.ArrayList")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithStaticImportStatement() throws IOException {
		Map<String, List<String>> classes = Map.of(
			"String", Arrays.asList("java.lang.String")
		);
		String java = "import static org.junit.Assert.*; public class Test { public static void main(String[] args){ assertEquals(true, true); }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("org.junit.Assert.*")).findFirst().isPresent());
		assertEquals(true, result.imports.stream().filter(e -> e.isStatic == true).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithElseStatement() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"MyObject", Arrays.asList("abc.MyObject")
		);
		String java = "public class Dummy { public void dummy(){ if(false){ } else { MyObject obj = null; } }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.MyObject")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithMultipleOccurences() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of();
		String java = "public class Dummy {\n" + 
			"@Test public void a(){ }\n" +
			"@Test public void b(){ }\n" +
			"@Test public void c(){ }\n" + 
			"}";

		try(InputStream input = new ByteArrayInputStream(java.getBytes(StandardCharsets.UTF_8))){ 
			ParseResult result = new ParseAction(FileSystems.getDefault(), classes).parse(input);

			assertEquals(true, result.errorMessages.isEmpty());
			assertEquals(true, result.imports.isEmpty());
			assertEquals(1, result.types.size());

			assertEquals(true, result.types.stream().filter(e -> e.value.equals("Test")).findFirst().isPresent());

			FileTypeEntry entry = result.types.get(0);

			assertEquals("Test", entry.value);
			assertEquals(2, entry.position.line);
			assertEquals(1, entry.position.column);
		}
	}

	@Test
	public void testParseJavaSourceWithPreviousChoice() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"List", Arrays.asList("java.awt.List", "java.util.List", "abc.util.List")
		);
		Map<String, List<String>> choices = Map.<String, List<String>>of(
			"List", Arrays.asList("abc.util.List")
		);
		String java = "public class Dummy { public void dummy(){ List l = null; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes, choices).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(1, result.imports.size());
		assertEquals(true, result.types.isEmpty());

		assertEquals(true, result.imports.stream().filter(e -> e.value.equals("abc.util.List")).findFirst().isPresent());
	}

	@Test
	public void testParseJavaSourceWithPreviousChoiceThatNoLongerExists() throws IOException {
		Map<String, List<String>> classes = Map.<String, List<String>>of(
			"List", Arrays.asList("java.awt.List", "java.util.List")
		);
		Map<String, List<String>> choices = Map.<String, List<String>>of(
			"List", Arrays.asList("abc.util.List")
		);
		String java = "public class Dummy { public void dummy(){ List l = null; }}";

		ParseResult result = new ParseAction(FileSystems.getDefault(), classes, choices).parseJavaSource(java);

		assertEquals(true, result.errorMessages.isEmpty());
		assertEquals(true, result.imports.isEmpty());
		assertEquals(1, result.types.size());

		FileTypeEntry entry = result.types.get(0);

		assertEquals("List", entry.value);
		assertEquals(2, entry.choices.size());

		assertEquals(true, entry.choices.stream().filter(e -> e.equals("java.awt.List")).findFirst().isPresent());
		assertEquals(true, entry.choices.stream().filter(e -> e.equals("java.util.List")).findFirst().isPresent());
	}
}
