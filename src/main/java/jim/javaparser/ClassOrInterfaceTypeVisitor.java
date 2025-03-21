package jim.javaparser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import jim.models.FileTypeEntry;
import com.github.javaparser.ast.expr.ClassExpr;

//TODO - rename ParseActionVisitor
//TODO - Annotations not found - @Test
public class ClassOrInterfaceTypeVisitor extends VoidVisitorAdapter<Map<String, FileTypeEntry>> {
	private final Pattern classNamePattern = Pattern.compile("[A-Z]+[A-Za-z0-9_$]*");

	private void setRange(FileTypeEntry entry, Node node){
		Optional<Range> range = node.getRange();

		if(range.isPresent()){
			Range r = range.get();

			entry.position.line = r.begin.line;
			entry.position.column = r.begin.column;
		}
	}

	private void addType(Map<String, FileTypeEntry> entries, ClassOrInterfaceType type){
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

		entries.put(entry.value, entry);
	}

	private void addTypeArguments(Map<String, FileTypeEntry> entries, ClassOrInterfaceType type){
		Optional<NodeList<Type>> opt = type.getTypeArguments();

		if(opt.isPresent()){
			for(Type t : opt.get()){
				if(t.isClassOrInterfaceType()){
					visit((ClassOrInterfaceType) t, entries);
				}
			}
		}
	}

	@Override
	public void visit(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
		addType(entries, type);	
		addTypeArguments(entries, type);
	}
	
	@Override
	public void visit(TypeParameter parameter, Map<String, FileTypeEntry> entries){
		entries.remove(parameter.getName().asString());
	}

	@Override
	public void visit(ObjectCreationExpr expression, Map<String, FileTypeEntry> entries){
		visit(expression.getType(), entries);
	}

	private boolean isClassName(SimpleName name){
		return classNamePattern.matcher(name.asString()).matches();
	}

	private void processFieldAccessExpr(Map<String, FileTypeEntry> entries, FieldAccessExpr field){
		Expression exp = field;

		while(!exp.isNameExpr()){
			if(exp.isFieldAccessExpr()){
				exp = exp.asFieldAccessExpr().getScope();
			}
		}
		
		NameExpr name = exp.asNameExpr();
		SimpleName nm = name.getName();

		if(isClassName(nm)){
			ClassOrInterfaceType type = new ClassOrInterfaceType();
			type.setName(nm);
			type.setRange(name.getRange().get());

			visit(type, entries);
		}
	}

	@Override
	public void visit(MethodCallExpr expression, Map<String, FileTypeEntry> entries){
		for(Expression ex : expression.getArguments()){
			if(ex.isFieldAccessExpr()){
				processFieldAccessExpr(entries, ex.asFieldAccessExpr());
			}
		}

		Optional<Expression> opt = expression.getScope();

		if(opt.isPresent()){
			Expression exp = opt.get();

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
			else if(exp.isMethodCallExpr()){
				visit(exp.asMethodCallExpr(), entries);
			}
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

	private void processClassExpr(Map<String, FileTypeEntry> entries, ClassExpr expr){
		Type type = expr.getType();

		if(type.isClassOrInterfaceType()){
			visit(type.asClassOrInterfaceType(), entries);
		}
	}

	@Override
	public void visit(NormalAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());

		for(MemberValuePair pair : expression.getPairs()){
			Expression value = pair.getValue();

			if(value.isFieldAccessExpr()){
				processFieldAccessExpr(entries, value.asFieldAccessExpr());
			}
			else if(value.isClassExpr()){
				processClassExpr(entries, value.asClassExpr());
			}
		}
	}	

	@Override
	public void visit(SingleMemberAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());

		Expression expr = expression.getMemberValue();

		if(expr.isFieldAccessExpr()){
			processFieldAccessExpr(entries, expr.asFieldAccessExpr());
		}
	}	

	@Override
	public void visit(MarkerAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());
	}
}
