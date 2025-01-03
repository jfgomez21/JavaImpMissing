import vim
import re
import sys
import os

from jis import Sorter

PACKAGE_REGEX = "^\\s*package\\s+"
IMPORT_REGEX = "^\\s*import\\s+"
SINGLE_LINE_COMMENT_REGEX = "^\\s*//"
CLASS_NAME_REGEX = "(^|<|\\(|\\t| |,)([A-Z]+[A-Za-z0-9_$]*)"

class JavaFile:
    def __init__(self):
        self.package_line_number = -1
        self.first_import_line_number = -1
        self.last_import_line_number = -1
        
        self.package_name = None
        self.file_imports = {}
        self.new_imports = {}
        self.found_imports = {}

def load_java_imp_class_file(filepath):
    results = {}
    
    if os.path.isfile(filepath):
        with open(filepath, "r") as file:
            for line in file:
                values = line.strip().split(" ")

                if len(values) > 1:
                    className = values[0]

                    if not className in results:
                        results[className] = list()

                    for package in values[1:]:
                        results[className].append(package)

    return results

def read_java_buffer():
    results = JavaFile()
    statements = list()
    
    importPattern = re.compile(IMPORT_REGEX)
    packagePattern = re.compile(PACKAGE_REGEX)

    for lineNum, line in enumerate(vim.current.buffer):
        if results.first_import_line_number == -1 and packagePattern.match(line):
            results.package_name = line[len("package ") : len(line) - 1]
            results.package_line_number = lineNum
            
        if importPattern.match(line):
            # Indicate the Start of the Import Statement Range if not yet set.
            if results.first_import_line_number == -1:
                results.first_import_line_number = lineNum
    
            statements.append(line)
            results.last_import_line_number = lineNum

    if results.first_import_line_number == -1 and results.package_line_number > -1:
        results.first_import_line_number = file.package_line_number

    if results.last_import_line_number == -1 and results.package_line_number > -1:
        results.last_import_line_number = file.package_line_number

    for statement in statements:
        index = statement.rindex(".")
        end = len(statement) - 1

        className = statement[index + 1:end]

        if not className in results.file_imports:
            results.file_imports[className] = list()

        results.file_imports[className].append(statement[len("imports ") : end])
    
    return results

def is_same_package(pkg1, pkg2):
    index = pkg2.rindex(".")

    return pkg1 == pkg2[0:index]

def process_java_buffer(javaImpClasses, javaImpChoices, file):
    p1 = re.compile(SINGLE_LINE_COMMENT_REGEX)
    p2 = re.compile(CLASS_NAME_REGEX)

    for line in vim.current.buffer[file.last_import_line_number + 1:]:
        results = p1.findall(line)
        
        if not results:
            results = p2.findall(line)
        
            if results:
                for result in results:
                    className = result[1]
                    
                    #TODO - handle if import not found

                    if not className in file.found_imports:
                        if className in file.file_imports:
                            file.found_imports[className] = file.file_imports[className]
                        elif className in javaImpChoices:
                            #TODO - handle if multiple choices exists

                            file.new_imports[className] = javaImpChoices[className][0]
                            file.found_imports[className] = javaImpChoices[className][0]
                        elif className in javaImpClasses:
                            #TODO - handle if multiple choices exists

                            packages = javaImpClasses[className]

                            if len(packages) == 1:
                                file.new_imports[className] = packages[0]
                                file.found_imports[className] = packages[0]
                            
def insert_import_statements(file, sort, replace):
    statements = list()

    if file.package_line_number > -1 and len(file.file_imports) < 1:
        statements.append("");

    for package in file.new_imports.values():
        if not package.startswith("java.lang") and not is_same_package(file.package_name, package):
            statements.append("import {0};".format(package))
    
    index = file.last_import_line_number + 1

    vim.current.buffer.append(statements, index)

    if vim.current.buffer[index + len(statements)].strip() != "":
        vim.current.buffer.append("", index + len(statements))

    if sort:
        Sorter()

javaImpClasses = load_java_imp_class_file(vim.eval("g:JavaImpClassList"))
javaImpChoices = load_java_imp_class_file("{0}/choices.txt".format(vim.eval("g:JavaImpDataDir")))
file = read_java_buffer()

process_java_buffer(javaImpClasses, javaImpChoices, file)
insert_import_statements(file, True, False)
