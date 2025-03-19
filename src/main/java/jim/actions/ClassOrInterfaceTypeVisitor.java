package jim.actions;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Optional;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.type.TypeParameter;

import jim.models.FileTypeEntry;

//TODO - move to jim.javaparser
//TODO - class declaration node on the current file should be ignored when not in known entries
public class ClassOrInterfaceTypeVisitor extends VoidVisitorAdapter<Collection<FileTypeEntry>> {
	private void setRange(FileTypeEntry entry, Node node){
		Optional<Range> range = node.getRange();

		if(range.isPresent()){
			Range r = range.get();

			entry.position.line = r.begin.line;
			entry.position.column = r.begin.column;
		}
	}

	@Override
	public void visit(ClassOrInterfaceType type, Collection<FileTypeEntry> entries){
		Deque<String> deque = new ArrayDeque<>();
		deque.add(type.getName().asString());

		Optional<ClassOrInterfaceType> opt = type.getScope();
		ClassOrInterfaceType parent = type;
		
		while(opt.isPresent()){
			parent = opt.get();

			deque.add(parent.getName().asString());

			opt = parent.getScope();
		}

		StringBuilder str = new StringBuilder();

		while(!deque.isEmpty()){
			str.append(deque.pollLast());
			str.append(".");
		}

		str.setLength(Math.max(0, str.length() - 1));

		FileTypeEntry entry = new FileTypeEntry();
		entry.value = str.toString();

		setRange(entry, parent);

		entries.add(entry);
	}
	
	@Override
	public void visit(TypeParameter parameter, Collection<FileTypeEntry> entries){
		FileTypeEntry entry = new FileTypeEntry();
		entry.value = parameter.getName().asString();

		entries.remove(entry);
	}

}
