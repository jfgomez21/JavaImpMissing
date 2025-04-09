import json
import os
import subprocess
import vim

from jis import Sorter

#TODO - add logging

def jim_load_java_imp_class_file(filepath):
    results = {}
    
    if os.path.isfile(filepath):
        with open(filepath, "r") as file:
            for line in file:
                values = line.strip().split(" ")

                if len(values) > 1:
                    className = values[0]
                    
                    if not className in results:
                        results[className] = []
                        
                        for package in values[1:]:
                            results[className].append(package)
    
    return results

def jim_run_java_command():
    opts = vim.eval("g:JimJavaOpts")
    plugin_path = vim.eval("s:pluginHome")
    jar_file = "{0}/java/jim-1.0-jar-with-dependencies.jar".format(plugin_path)

    arguments = ["java"]

    if opts:
        arguments.extend(opts.split(" "))

    arguments.append("-jar")
    arguments.append(jar_file)

    filename = vim.eval("g:JavaImpClassList")

    if os.path.isfile(filename):
        arguments.append("--class-file")
        arguments.append(filename)

    filename = "{0}/choices.txt".format(vim.eval("g:JavaImpDataDir"))

    if os.path.isfile(filename):
        arguments.append("--choice-file")
        arguments.append(filename)

    arguments.append(vim.current.buffer.name)

    return subprocess.run(arguments, capture_output=True, text=True)

def jim_show_error_message(msg):
    try:
        vim.command("echohl ErrorMsg")
        vim.command("echo \"{0}\"".format(msg))
        vim.command("echohl None")
    except vim.error:
        pass

def jim_select_choice(className, choices):
    prompt = "Multiple matches exist for {0}. Select one -".format(className)

    for index, choice in enumerate(choices):
        prompt = "{0}\n{1} - {2}".format(prompt, index + 1, choice)

    skip_option = len(choices) + 1

    prompt = "{0}\n{1} - {2}\n".format(prompt, skip_option, "skip")

    vim.command("echohl MoreMsg")
    choice = vim.eval("input(\"{0}\")".format(prompt))
    vim.command("echohl None")

    selection = None

    if choice.isdigit():
        index = int(float(choice)) - 1

        if index >= 0 and index < len(choices):
            selection = choices[index]

    return selection

def jim_update_choices(choices, name, selection):
    values = None

    if name not in choices:
        values = []

        choices[name] = values
    else:
        values = choices[name]
    
    if selection in values:
        values.remove(selection)
    
    values.insert(0, selection)

def jim_save_java_imp_class_file(filename, classes):
    with open(filename, "w") as file:
        for name, values in classes.items():
            line = name

            for value in values:
                line = "{0} {1}".format(line, value)

            file.write(line)
            file.write("\n")
    
def jim_process_results(js):
    filename = "{0}/choices.txt".format(vim.eval("g:JavaImpDataDir"))
    choices  = jim_load_java_imp_class_file(filename)

    for identifier in js["types"]:
        line = identifier["position"]["line"]
        column = identifier["position"]["column"]
        count = len(identifier["value"])

        text = vim.current.buffer[line - 1][column - 1]

        if text.startswith("@"):
            count = count + 1

        vim.eval("cursor({0}, {1})".format(line, 1))
        match_id = vim.eval("matchaddpos(\"{0}\", [[{1}, {2}, {3}]])".format("Search", line, column, count))
        vim.command("redraw")

        if identifier["choices"]:
            selection = jim_select_choice(identifier["value"], identifier["choices"])

            if selection:
                js["imports"].append({"value" : selection}) 

                jim_update_choices(choices, identifier["value"], selection) 
        else:
            vim.command("echohl MoreMsg")
            vim.eval("input(\"{0}\")".format("No match found for {0}.".format(identifier["value"])))
            vim.command("echohl None")

        vim.eval("matchdelete({0})".format(match_id))

    jim_save_java_imp_class_file(filename, choices)

def jim_insert_import_statements(js):
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
        if "static" in import_statement and  import_statement["static"]:
            line = "import static {0};".format(import_statement["value"])
        else:
            line = "import {0};".format(import_statement["value"])

        vim.current.buffer.append(line, start_line + index)

    import_count = len(js["imports"])

    if import_count > 0:
        vim.current.buffer.append("", start_line + import_count)

def jim_import_missing():
    cursor_position = vim.eval("getcurpos()")

    result = jim_run_java_command()

    if result.returncode != 0:
        message = result.stdout

        if message == "":
            message = result.stderr
        
        jim_show_error_message(message)

        return

    js = json.loads(result.stdout) 

    if js["errorMessages"]:
        jim_show_error_message("\n".join(js["errorMessages"]))
        return

    jim_process_results(js)
    jim_insert_import_statements(js)

    if js["imports"]:
        Sorter()

    if js["types"]:
        vim.eval("cursor({0}, {1})".format(cursor_position[1], cursor_position[2]))

    vim.command("redraw")
