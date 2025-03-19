import json
import os
import re
import subprocess
import sys
import vim

from jis import Sorter

#TODO - add logging

def load_java_imp_class_file(filepath):
    results = {}
    
    if os.path.isfile(filepath):
        with open(filepath, "r") as file:
            for line in file:
                values = line.strip().split(" ")

                if len(values) > 1:
                    className = values[0]
                    results[className] = values[1]

    return results

def parse_java_file():
    plugin_path = os.path.dirname(os.path.dirname(sys.argv[0]))
    jar_file = "{0}/java/jim-1.0-jar-with-dependencies.jar".format(plugin_path)

    args = ["java", "-jar", jar_file, "parse", vim.current.buffer.name]

    result = subprocess.run(args, capture_output=True, text=True)

    return result

def show_error_message(msg):
    vim.command("echohl ErrorMsg")
    vim.command("echo \"{0}\"".format(msg))
    vim.command("echohl None")

#TODO - save choices

def process_json_results(js):
    for identifier in js["types"]:
        line = identifier["position"]["line"]
        column = identifier["position"]["column"]

        vim.eval("cursor({0}, {1})".format(line, 1))
        match_id = vim.eval("matchaddpos(\"{0}\", [[{1}, {2}, {3}]])".format("Search", line, column, len(identifier["value"])))
        vim.command("redraw")

        if identifier["choices"]:
            prompt = "Multiple matches exist for {0}. Select one -".format(identifier["value"])

            for index, choice in enumerate(identifier["choices"]):
                prompt = "{0}\n{1} - {2}".format(prompt, index + 1, choice)

            skip_option = len(identifier["choices"]) + 1

            prompt = "{0}\n{1} - {2}\n".format(prompt, skip_option, "skip")

            vim.command("echohl MoreMsg")
            choice = vim.eval("input(\"{0}\")".format(prompt))
            vim.command("echohl None")

            if choice.isdigit():
                index = int(float(choice)) - 1

                if index >= 0 and index < len(identifier["choices"]):
                    js["imports"].append({"value" : identifier["choices"][index]}) 
        else:
            vim.command("echohl MoreMsg")
            vim.eval("input(\"{0}\")".format("No match found for {0}.".format(identifier["value"])))
            vim.command("echohl None")

        vim.eval("matchdelete({0})".format(match_id))

def insert_import_statements(js):
    start_line = js["firstImportStatementLine"]
    end_line = js["lastImportStatementLine"]

    if start_line > 0:
        del vim.current.buffer[start_line - 1 : end_line]

    package_line = js["package"]["position"]["line"]

    if package_line > 0:
        start_line = package_line

        while not vim.current.buffer[start_line].strip():
            del vim.current.buffer[start_line]

        vim.current.buffer.append("", start_line)

        start_line = start_line + 1
    else:
        start_line = 1

    for index, import_statement in enumerate(js["imports"]):
        vim.current.buffer.append("import {0};".format(import_statement["value"]), start_line + index)

    vim.current.buffer.append("", start_line + len(js["imports"]))

def execute():
    #javaImpChoices = load_java_imp_class_file(choices)
    cursor_position = vim.eval("getcurpos()")
    result = parse_java_file()

    if result.returncode != 0:
        message = result.stdout

        if message == "":
            message = result.stderr
        
        show_error_message(message)

        return

    js = json.loads(result.stdout) 

    if js["errorMessages"]:
        show_error_message("\n".join(js["errorMessages"]))
        return

    process_json_results(js)
    insert_import_statements(js)

    Sorter()

    if js["types"]:
        vim.eval("cursor({0}, {1})".format(cursor_position[1], cursor_position[2]))

    vim.command("redraw")

if __name__ == '__main__':
    execute()
