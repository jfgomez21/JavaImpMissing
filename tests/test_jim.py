import subprocess
import tempfile
import unittest
import json

from pyfakefs.fake_filesystem_unittest import TestCase
from unittest.mock import patch

import vim
import jim


class TestJavaImpMissing(TestCase):
    def setUp(self):
        self.setUpPyfakefs() 
        vim.reset()

    def test_jim_load_java_imp_class_file(self):
        contents = [
            "ArrayList java.util.ArrayList",
            "List java.util.List",
            ""
        ]
        self.fs.create_file(".JavaImp/JavaImp.text", contents="\n".join(contents)) 

        results = jim.jim_load_java_imp_class_file(".JavaImp/JavaImp.text")

        self.assertEqual(2, len(results))

        self.assertEqual(1, len(results["ArrayList"]))
        self.assertEqual("java.util.ArrayList", results["ArrayList"][0])

        self.assertEqual(1, len(results["List"]))
        self.assertEqual("java.util.List", results["List"][0])

    @patch('subprocess.run')
    def test_jim_run_java_command(self, mock_run):
        self.fs.create_file(".JavaImp/JavaImp.text")
        self.fs.create_file(".JavaImp/choices.txt")

        vim.set_eval("g:JimJavaOpts", "-XX:SomeValue")
        vim.set_eval("s:pluginHome", "/home/user/.vim")
        vim.set_eval("g:JavaImpClassList", ".JavaImp/JavaImp.text")
        vim.set_eval("g:JavaImpDataDir", ".JavaImp")
        vim.current.buffer.name = "MyClass.java"

        mock_run.return_value.stdout = "{}"
        mock_run.return_value.returncode = 0

        results = jim.jim_run_java_command()
        
        self.assertEqual(0, results.returncode)
        self.assertEqual("{}", results.stdout)

        expected_args = [
            "java",
            "-XX:SomeValue",
            "-jar",
            "/home/user/.vim/java/jim-1.0-jar-with-dependencies.jar",
            "--class-file",
            ".JavaImp/JavaImp.text",
            "--choice-file",
            ".JavaImp/choices.txt",
            vim.current.buffer.name
        ]

        mock_run.assert_called_once_with(expected_args, capture_output=True, text=True)

    def test_jim_show_error_message(self):
        jim.jim_show_error_message("Error message")

        self.assertEqual(1, len(vim.properties["error_messages"]))
        self.assertEqual("Error message", vim.properties["error_messages"][0])
    
    def test_jim_select_choice(self):
        vim.set_input_return_value("2")

        expected_choices = [
            "Multiple matches exist for com.example.service.MyService. Select one -",
            "1 - com.example.service.MyService",
            "2 - com.module.service.MyService",
            "3 - skip"
        ]

        result = jim.jim_select_choice("com.example.service.MyService", ["com.example.service.MyService", "com.module.service.MyService"])

        self.assertEqual("com.module.service.MyService", result)

        for index in range(min(len(expected_choices), len(vim.properties["input_messages"]))):
            self.assertEqual(expected_choices[index], vim.properties["input_messages"][index])

        self.assertEqual(4, len(vim.properties["input_messages"]))

    def test_jim_select_choice_with_skip(self):
        vim.set_input_return_value("3")

        expected_choices = [
            "Multiple matches exist for com.example.service.MyService. Select one -",
            "1 - com.example.service.MyService",
            "2 - com.module.service.MyService",
            "3 - skip"
        ]

        result = jim.jim_select_choice("com.example.service.MyService", ["com.example.service.MyService", "com.module.service.MyService"])

        self.assertEqual(None, result)

        for index in range(min(len(expected_choices), len(vim.properties["input_messages"]))):
            self.assertEqual(expected_choices[index], vim.properties["input_messages"][index])

        self.assertEqual(4, len(vim.properties["input_messages"]))

    def test_jim_update_choices(self):
        choices = {}

        jim.jim_update_choices(choices, "MyService", "com.module.service.MyService")

        self.assertEqual(1, len(choices))
        self.assertEqual(1, len(choices["MyService"]))
        self.assertEqual("com.module.service.MyService", choices["MyService"][0])  

    def test_jim_update_choices_with_existing_value(self):
        choices = {"MyService": ["com.example.service.MyService", "com.module.service.MyService"]}

        jim.jim_update_choices(choices, "MyService", "com.module.service.MyService")

        self.assertEqual(1, len(choices))
        self.assertEqual(2, len(choices["MyService"]))
        self.assertEqual("com.module.service.MyService", choices["MyService"][0])  
        self.assertEqual("com.example.service.MyService", choices["MyService"][1])  

    def test_jim_save_java_imp_class_file(self):
        self.fs.create_file(".JavaImp/choices.text")

        classes = {"MyService" : ["com.example.service.MyService", "com.module.service.MyService"]}

        jim.jim_save_java_imp_class_file(".JavaImp/choices.text", classes)

        results = []

        with open(".JavaImp/choices.text", "r") as file:
            for line in file:
                results.append(line.strip())

        self.assertEqual(1, len(results))
        self.assertEqual("MyService com.example.service.MyService com.module.service.MyService", results[0])
    
    def test_jim_process_results(self):
        self.fs.create_file(".JavaImp/choices.txt")

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")
        vim.set_input_return_value("2")
        vim.current.buffer.append(" ")
    
        js = {
            "types": [
                {
                    "position": {
                        "line": 1,
                        "column": 1
                    },
                    "value" : "MyService",
                    "choices": ["com.module.service.MyService", "com.example.service.MyService"]
                }
            ],
            "imports" : []
        }

        jim.jim_process_results(js)

        self.assertEqual(1, len(js["imports"]))
        self.assertEqual("com.example.service.MyService", js["imports"][0]["value"])

        choices = []

        with open(".JavaImp/choices.txt", "r") as file:
            for line in file:
                choices.append(line.strip())

        self.assertEqual(1, len(choices))
        self.assertEqual("MyService com.example.service.MyService", choices[0])

    def test_jim_process_results_with_no_choices(self):
        self.fs.create_file(".JavaImp/choices.txt")

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")
        vim.current.buffer.append(" ")
    
        js = {
            "types": [
                {
                    "position": {
                        "line": 1,
                        "column": 1
                    },
                    "value" : "MyService",
                    "choices": []
                }
            ],
            "imports" : []
        }

        jim.jim_process_results(js)

        self.assertEqual(0, len(js["imports"]))

        expected_input_messages = ["No match found for MyService."]

        for index in range(min(len(expected_input_messages), len(vim.properties["input_messages"]))):
            self.assertEqual(expected_input_messages[index], vim.properties["input_messages"][index])

        self.assertEqual(len(expected_input_messages), len(vim.properties["input_messages"]))

    def test_jim_process_results_with_annotations(self):
        self.fs.create_file(".JavaImp/choices.txt")

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")
        vim.set_input_return_value("1")
        vim.current.buffer.append("@Test")
    
        js = {
            "types": [
                {
                    "position": {
                        "line": 1,
                        "column": 1
                    },
                    "value" : "Test",
                    "choices": ["org.junit.Test", "com.example.annotations.Test"]
                }
            ],
            "imports" : []
        }

        jim.jim_process_results(js)

        self.assertEqual(1, len(js["imports"]))
        self.assertEqual("org.junit.Test", js["imports"][0]["value"])

        choices = []

        with open(".JavaImp/choices.txt", "r") as file:
            for line in file:
                choices.append(line.strip())

        self.assertEqual(1, len(choices))
        self.assertEqual("Test org.junit.Test", choices[0])

    def test_jim_insert_import_statements(self):
        js = {
            "firstImportStatementLine" : 0,
            "lastImportStatementLine" : 0,
            "package" : { 
                "position" : { "line" : 1, "columm" : 1 },
                "value" : "com.example.services"
            },
            "imports" : [
                { "static" : False, "value" : "com.example.services.MyService" }
            ]
        }

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        jim.jim_insert_import_statements(js)

        expected_results = [
            "package com.example;",
            "",
            "import com.example.services.MyService;",
            "",
            "public class MyObject {",
            "",
            "}"
        ]

        for index in range(min(len(expected_results), len(vim.current.buffer))):
            self.assertEqual(expected_results[index], vim.current.buffer[index])

        self.assertEqual(len(expected_results), len(vim.current.buffer))

    def test_jim_insert_import_statements_with_unused_imports(self):
        js = {
            "firstImportStatementLine" : 3,
            "lastImportStatementLine" : 4,
            "package" : { 
                "position" : { "line" : 1, "columm" : 1 },
                "value" : "com.example.services"
            },
            "imports" : [
                { "static" : False, "value" : "com.example.services.MyService" }
            ]
        }

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("")
        vim.current.buffer.append("import java.util.ArrayList;")
        vim.current.buffer.append("import java.util.List;")
        vim.current.buffer.append("")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        jim.jim_insert_import_statements(js)

        expected_results = [
            "package com.example;",
            "",
            "import com.example.services.MyService;",
            "",
            "public class MyObject {",
            "",
            "}"
        ]

        for index in range(min(len(expected_results), len(vim.current.buffer))):
            self.assertEqual(expected_results[index], vim.current.buffer[index])

        self.assertEqual(len(expected_results), len(vim.current.buffer))

    def test_jim_insert_import_statements_with_no_package(self):
        js = {
            "firstImportStatementLine" : 0,
            "lastImportStatementLine" : 0,
            "package" : { 
                "position" : { "line" : 0, "columm" : 0 },
            },
            "imports" : [
                { "static" : False, "value" : "com.example.services.MyService" }
            ]
        }

        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        jim.jim_insert_import_statements(js)

        expected_results = [
            "import com.example.services.MyService;",
            "",
            "public class MyObject {",
            "",
            "}"
        ]

        for index in range(min(len(expected_results), len(vim.current.buffer))):
            self.assertEqual(expected_results[index], vim.current.buffer[index])

        self.assertEqual(len(expected_results), len(vim.current.buffer))

    def test_jim_insert_import_statements_with_static_imports(self):
        js = {
            "firstImportStatementLine" : 2,
            "lastImportStatementLine" : 2,
            "package" : { 
                "position" : { "line" : 1, "columm" : 1 },
                "value" : "com.example.services"
            },
            "imports" : [
                { "static" : False, "value" : "com.example.services.MyService" },
                { "static" : True, "value" : "org.junit.Assert.*" }
            ]
        }

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("import static org.junit.Assert.*;")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        jim.jim_insert_import_statements(js)

        expected_results = [
            "package com.example;",
            "",
            "import com.example.services.MyService;",
            "import static org.junit.Assert.*;",
            "",
            "public class MyObject {",
            "",
            "}"
        ]

        for index in range(min(len(expected_results), len(vim.current.buffer))):
            self.assertEqual(expected_results[index], vim.current.buffer[index])

        self.assertEqual(len(expected_results), len(vim.current.buffer))
    
    @patch('subprocess.run')
    def test_jim_import_missing(self, mock_run):
        self.fs.create_file(".JavaImp/JavaImp.text")
        self.fs.create_file(".JavaImp/choices.txt")

        js = {
            "firstImportStatementLine" : 0,
            "lastImportStatementLine" : 0,
            "package" : { 
                "position" : { "line" : 1, "columm" : 1 },
                "value" : "com.example.services"
            },
            "imports" : [
                { "static" : False, "value" : "com.example.services.MyService" }
            ],
            "errorMessages" : [],
            "types" : []
        }

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        mock_run.return_value.stdout = json.dumps(js)
        mock_run.return_value.returncode = 0

        jim.jim_import_missing()

        expected_results = [
            "package com.example;",
            "",
            "import com.example.services.MyService;",
            "",
            "public class MyObject {",
            "",
            "}"
        ]

        for index in range(min(len(expected_results), len(vim.current.buffer))):
            self.assertEqual(expected_results[index], vim.current.buffer[index])

        self.assertEqual(len(expected_results), len(vim.current.buffer))

    @patch('subprocess.run')
    def test_jim_import_missing_with_failed_java_command_stdout(self, mock_run):
        self.fs.create_file(".JavaImp/JavaImp.text")
        self.fs.create_file(".JavaImp/choices.txt")

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        mock_run.return_value.stdout = "Failed to execute java command"
        mock_run.return_value.returncode = -1

        jim.jim_import_missing()

        expected_error_messages = ["Failed to execute java command"]
        
        for index in range(min(len(expected_error_messages), len(vim.properties["error_messages"]))):
            self.assertEqual(expected_error_messages[index], vim.properties["error_messages"][index])

        self.assertEqual(len(expected_error_messages), len(vim.properties["error_messages"]))

    @patch('subprocess.run')
    def test_jim_import_missing_with_failed_java_command_stderr(self, mock_run):
        self.fs.create_file(".JavaImp/JavaImp.text")
        self.fs.create_file(".JavaImp/choices.txt")

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        mock_run.return_value.stdout = ""
        mock_run.return_value.stderr = "Failed to execute java command"
        mock_run.return_value.returncode = -1

        jim.jim_import_missing()

        expected_error_messages = ["Failed to execute java command"]
        
        for index in range(min(len(expected_error_messages), len(vim.properties["error_messages"]))):
            self.assertEqual(expected_error_messages[index], vim.properties["error_messages"][index])

        self.assertEqual(len(expected_error_messages), len(vim.properties["error_messages"]))

    @patch('subprocess.run')
    def test_jim_import_missing_with_error_messages(self, mock_run):
        self.fs.create_file(".JavaImp/JavaImp.text")
        self.fs.create_file(".JavaImp/choices.txt")

        js = {"errorMessages" : ["Parse Error - 1, 1", "Error ABC"]}

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("")
        vim.current.buffer.append("}") 

        mock_run.return_value.stdout = json.dumps(js)
        mock_run.return_value.returncode = 0

        jim.jim_import_missing()

        expected_results = [
            "package com.example;",
            "",
            "import com.example.services.MyService;",
            "",
            "public class MyObject {",
            "",
            "}"
        ]

        for index in range(min(len(js["errorMessages"]), len(vim.properties["error_messages"]))):
            self.assertEqual(js["errorMessages"][index], vim.properties["error_messages"][index])

        self.assertEqual(len(js["errorMessages"]), len(vim.properties["error_messages"]))

    @patch('subprocess.run')
    def test_jim_import_missing_with_types(self, mock_run):
        self.fs.create_file(".JavaImp/JavaImp.text")
        self.fs.create_file(".JavaImp/choices.txt")

        js = {
            "firstImportStatementLine" : 0,
            "lastImportStatementLine" : 0,
            "package" : { 
                "position" : { "line" : 1, "columm" : 1 },
                "value" : "com.example"
            },
            "imports" : [],
            "errorMessages" : [],
            "types" : [
                {
                    "position" : { "line" : 4, "column" : 1 },
                    "value" : "MyService",
                    "choices" : ["com.example.services.MyService", "com.module.services.MyService"]
                }
            ]
        }

        vim.set_eval("g:JavaImpDataDir", ".JavaImp")
        vim.set_input_return_value("1")

        vim.current.buffer.append("package com.example;")
        vim.current.buffer.append("")
        vim.current.buffer.append("public class MyObject {")
        vim.current.buffer.append("MyService")
        vim.current.buffer.append("}") 

        mock_run.return_value.stdout = json.dumps(js)
        mock_run.return_value.returncode = 0

        jim.jim_import_missing()

        expected_results = [
            "package com.example;",
            "",
            "import com.example.services.MyService;",
            "",
            "public class MyObject {",
            "MyService",
            "}"
        ]

        for index in range(min(len(expected_results), len(vim.current.buffer))):
            self.assertEqual(expected_results[index], vim.current.buffer[index])

        self.assertEqual(len(expected_results), len(vim.current.buffer))

