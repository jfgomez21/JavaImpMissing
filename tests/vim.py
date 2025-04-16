class BufferList(list):
    name = ""
    def __init__(self, *args):
        super().__init__(*args)
    
    def append(self, element, index = None):
        if index is None:
            if isinstance(element, list):
                for item in element:
                    super().append(item)
            else:
                super().append(element)
        else:
            if isinstance(element, list):
                self[index:index] = element
            else:
                super().insert(index, element)
    
class Buffer:
    buffer = BufferList()

class error(BaseException):
    pass

current = Buffer()
properties = {}

def get_quoted_value(value):
    index = value.find('"')
    last_index = value.rfind('"')

    result = ""

    if index > -1 and last_index > -1:
        result = value[index + 1:last_index]
    
    return result

def add_value(dest, text):
    values = get_quoted_value(text).strip().split("\n")

    for value in values:
        dest.append(value)

def eval(arg):
    if arg in properties:
        return properties[arg]
    elif arg.startswith("input"):
        add_value(properties["input_messages"], arg)
    elif arg == "getcurpos()":
        return [0, 1, 1]

    return properties["input_return_value"]

def set_eval(name, value):
    properties[name] = value

def command(cmd):
    if cmd.startswith("tabe"):
        properties["file_name"] = cmd[5:]
    elif cmd.startswith("echo "):
        add_value(properties["error_messages"], cmd)

def set_input_return_value(code):
    properties["input_return_value"] = code

def reset():
    properties.clear()
    properties["file_name"] = ""
    properties["error_messages"] = []
    properties["input_messages"] = []
    properties["input_return_value"] = "0"

    current.buffer.name = ""
    current.buffer.clear()

