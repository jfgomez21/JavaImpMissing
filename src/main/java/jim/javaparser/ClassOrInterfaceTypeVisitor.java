package jim.javaparser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import jim.models.FileTypeEntry;

//TODO - rename ParseActionVisitor
public class ClassOrInterfaceTypeVisitor extends VoidVisitorAdapter<Map<String, FileTypeEntry>> {
	private final Pattern classNamePattern = Pattern.compile("[A-Z]+[A-Za-z0-9_$]*");
	private final Pattern upperCasePattern = Pattern.compile("[A-Z_$]+");

	private void println(Object obj){
		System.out.println(String.format("%s - %s", obj.getClass(), obj));
	}

	private void setRange(FileTypeEntry entry, Node node){
		Optional<Range> range = node.getRange();

		if(range.isPresent()){
			Range r = range.get();

			entry.position.line = r.begin.line;
			entry.position.column = r.begin.column;
		}
	}

	private int comparePositions(ClassOrInterfaceType parent, FileTypeEntry entry){
		Optional<Range> opt = parent.getRange();

		if(opt.isPresent()){
			Range range = opt.get();

			int value = range.begin.line - entry.position.line;

			if(value == 0){
				return range.begin.column - entry.position.column;	
			}

			return value;
		}

		return -1;
	}

	private void addType(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
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

		String name = str.toString();
		FileTypeEntry entry = entries.get(name);

		if(entry == null){
			entry = new FileTypeEntry();
			entry.value = name;

			setRange(entry, parent);

			entries.put(name, entry);
		}
		else{
			if(comparePositions(parent, entry) < 0){
				setRange(entry, parent);
			}
		}
	}

	private void processType(Type type, Map<String, FileTypeEntry> entries){
		if(type.isClassOrInterfaceType()){
			visit(type.asClassOrInterfaceType(), entries);
		}
	}

	private void addTypeArguments(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
		Optional<NodeList<Type>> opt = type.getTypeArguments();

		if(opt.isPresent()){
			for(Type t : opt.get()){
				processType(t, entries);
			}
		}
	}

	@Override
	public void visit(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
		addType(type, entries);	
		addTypeArguments(type, entries);
	}
	
	@Override
	public void visit(TypeParameter parameter, Map<String, FileTypeEntry> entries){
		entries.remove(parameter.getName().asString());
	}

	private boolean isClassName(SimpleName name){
		String nm = name.asString();

		return classNamePattern.matcher(nm).matches() && !upperCasePattern.matcher(nm).matches();
	}

	private void processClassExpr(ClassExpr expr, Map<String, FileTypeEntry> entries){
		processType(expr.getType(), entries);
	}

	private void processArrayInitializerExpr(ArrayInitializerExpr expr, Map<String, FileTypeEntry> entries){
		for(Expression exp : expr.getValues()){
			processExpression(exp, entries);
		}
	}

	private void processExpression(Expression exp, Map<String, FileTypeEntry> entries){
		if(exp.isNameExpr()){
			NameExpr nm = exp.asNameExpr();
			SimpleName name = nm.getName();

			if(isClassName(name)){
				ClassOrInterfaceType type = new ClassOrInterfaceType();
				type.setName(name);
				type.setRange(nm.getRange().get());

				visit(type, entries);
			}
		}
		else if(exp.isFieldAccessExpr()){
			visit(exp.asFieldAccessExpr(), entries);
		}
		else if(exp.isMethodCallExpr()){
			visit(exp.asMethodCallExpr(), entries);
		}
		else if(exp.isObjectCreationExpr()){
			visit(exp.asObjectCreationExpr(), entries);
		}
		else if(exp.isClassExpr()){
			processClassExpr(exp.asClassExpr(), entries);
		}
		else if(exp.isArrayInitializerExpr()){
			processArrayInitializerExpr(exp.asArrayInitializerExpr(), entries);	
		}
	}

	private void processAnnotationExpression(Map<String, FileTypeEntry> entries, Name name, Range range){
		Optional<Name> opt = name.getQualifier();

		if(opt.isEmpty()){
			ClassOrInterfaceType type = new ClassOrInterfaceType();
			type.setName(new SimpleName(name.getIdentifier()));
			type.setRange(range);

			visit(type, entries); 
		}
	}	

	@Override
	public void visit(NormalAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());

		for(MemberValuePair pair : expression.getPairs()){
			processExpression(pair.getValue(), entries);
		}
	}	

	@Override
	public void visit(SingleMemberAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());
		processExpression(expression.getMemberValue(), entries);
	}	

	@Override
	public void visit(MarkerAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());
	}

	@Override
	public void visit(FieldAccessExpr expression, Map<String, FileTypeEntry> entries){
		processExpression(expression.getScope(), entries);
	}
	
	@Override
	public void visit(MethodCallExpr expression, Map<String, FileTypeEntry> entries){
		super.visit(expression, entries);

		Optional<Expression> opt = expression.getScope();

		if(opt.isPresent()){
			processExpression(opt.get(), entries);
		}
	}	
}
