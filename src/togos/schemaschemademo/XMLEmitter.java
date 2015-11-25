package togos.schemaschemademo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class XMLEmitter
{
	public static class Attribute implements Comparable<Attribute> {
		public final String name;
		public final String value;
		public Attribute( String n, String v ) {
			this.name = n;
			this.value = v;
		}
		@Override public int compareTo(Attribute other) {
			int a = name.compareTo(other.name);
			if( a != 0 ) return a;
			return value.compareTo(other.value);
		}
	}
	
	enum State {
		TEXT,
		OPENING
	}
	
	public static List<Attribute> attrList(String...kv) {
		assert (kv.length & 1) == 0;
		ArrayList<Attribute> attrs = new ArrayList<Attribute>();
		for( int i=0; i+1<kv.length; i += 2 ) {
			attrs.add(new Attribute(kv[i], kv[i+1]));
		}
		return attrs;
	}
	
	protected final Appendable output;
	protected final ArrayList<String> openTags = new ArrayList<String>();
	protected State state = State.TEXT;
	
	public XMLEmitter( Appendable output ) {
		this.output = output;
	}
	
	protected String popTagName() {
		if( openTags.size() == 0 ) {
			throw new RuntimeException("No open tags to pop!");
		}
		return openTags.remove(openTags.size()-1);
	}
	
	public void text( String text ) throws IOException {
		switch( state ) {
		case OPENING:
			output.append(">");
			state = State.TEXT;
			break;
		case TEXT: break;
		default:
			throw new RuntimeException("Bad state: "+state);
		}
		output.append(
			text.
			replace("&", "&amp;").
			replace("<", "&lt;").
			replace(">", "&gt;")
		);
	}
	
	public void open( String tagName, Iterable<Attribute> attributes, boolean close ) throws IOException {
		text("");
		openTags.add(tagName);
		output.append("<"+tagName);
		for( Attribute attr : attributes ) {
			output.append(" ");
			output.append(attr.name);
			output.append("=\"");
			output.append(
				attr.value.
				replace("&", "&amp;").
				replace("<", "&lt;").
				replace(">", "&gt;").
				replace("\"", "&quot;")
			);
			output.append("\"");
		}
		state = State.OPENING;
		if( close ) close();
	}
	
	public void close() throws IOException {
		String closedTagName = popTagName();
		
		switch( state ) {
		case OPENING:
			output.append("/>");
			state = State.TEXT;
			break;
		case TEXT:
			output.append("</"+closedTagName+">");
			break;
		default:
			throw new RuntimeException("Bad state: "+state);
		}
	}
}
