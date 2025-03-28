package jim.actions;

import static org.junit.Assert.*;

import java.io.IOException;

import java.nio.file.FileSystems;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import jim.AbstractJimTest;

import jim.models.ParseResult;

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
}
