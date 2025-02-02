class BufferList(list):
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

current = Buffer()
