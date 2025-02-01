import unittest
import unittest.mock
import tempfile

import vim
import jim


class TestJavaImpMissing(unittest.TestCase):
	def setUp(self):
		vim.current.buffer.clear()

	def test_load_java_imp_class_file(self):
		with tempfile.NamedTemporaryFile(mode="w") as file:
			file.write("ArrayList java.util.ArrayList\n")
			file.write("List java.util.List java.awt.List org.eclipse.swt.widgets.List\n")
			file.write("Menu java.awt.Menu\n")
			file.write("Menu org.eclipse.swt.widgets.Menu\n")

			file.flush()

			results = jim.load_java_imp_class_file(file.name)

			expected = {
				"ArrayList" : ["java.util.ArrayList"],
				"List" : ["java.util.List", "java.awt.List", "org.eclipse.swt.widgets.List"],
				"Menu" : ["java.awt.Menu", "org.eclipse.swt.widgets.Menu"]
			}

			self.assertEqual(len(expected), len(results))

			for class_name, values in expected.items():
				self.assertEqual(True, class_name in results)
				self.assertEqual(len(values), len(results[class_name]))

				for value in values:
					self.assertEqual(True, value in results[class_name])

	def test_load_java_imp_class_file_file_not_found(self):
		results = jim.load_java_imp_class_file("abc.txt")
		
		self.assertEqual({}, results)
		self.assertEqual(0, len(results))
	
	def test_read_java_buffer(self):
		vim.current.buffer.append("package abc;")
		vim.current.buffer.append("import java.util.List;")

		results = jim.read_java_buffer()
		
		self.assertEqual(1, results.first_import_line_number)
		self.assertEqual(1, results.last_import_line_number)
		self.assertEqual(1, len(results.file_imports))
		self.assertEqual(True, "List" in results.file_imports)
		self.assertEqual("java.util.List", results.file_imports["List"])

	def test_read_java_buffer_no_imports(self):
		vim.current.buffer.append("package abc;")

		results = jim.read_java_buffer()

		self.assertEqual(1, results.first_import_line_number)
		self.assertEqual(1, results.last_import_line_number)
		self.assertEqual(0, len(results.file_imports))

	
	def test_process_java_buffer(self):
		vim.current.buffer.append("package abc;")
		vim.current.buffer.append("")
		vim.current.buffer.append("import java.util.List;")
		vim.current.buffer.append("")
		vim.current.buffer.append("List")
		vim.current.buffer.append("java.util.List")
		vim.current.buffer.append("ArrayList")
		vim.current.buffer.append("File")
		vim.current.buffer.append("AbcDef")
		vim.current.buffer.append("//Dummy")
		vim.current.buffer.append("//         Dummy")

		results = jim.JavaFile()
		results.package_line_number = 0
		results.first_import_line_number = 2
		results.last_import_line_number = 2
		results.package_name = "abc"
		results.file_imports = {"List" : "java.util.List"}

		classes = {"List" : ["java.util.List"], "File" : ["java.io.File"], "ArrayList" : ["java.util.ArrayList"], "AbcDef" : ["pk1.AbcDef", "pk2.AbcDef"], "Dummy" : ["abc.def.Dummy"]}
		choices = {"AbcDef" : ["pk2.AbcDef"]}

		jim.process_java_buffer(classes, choices, results)

		expected_found_imports = ["java.util.List", "java.io.File", "java.util.ArrayList", "pk2.AbcDef"]
		expected_new_imports = ["java.io.File", "java.util.ArrayList", "pk2.AbcDef"]

		self.assertEqual(len(expected_found_imports), len(results.found_imports))

		for value in expected_found_imports:
			key = value[value.rindex(".") + 1:]

			self.assertEqual(True, key in results.found_imports)
			self.assertEqual(value, results.found_imports[key])

		self.assertEqual(len(expected_new_imports), len(results.new_imports))

		for value in expected_new_imports:
			key = value[value.rindex(".") + 1:]

			self.assertEqual(True, key in results.new_imports)
			self.assertEqual(value, results.new_imports[key])




if __name__ == '__main__':
    unittest.main()

