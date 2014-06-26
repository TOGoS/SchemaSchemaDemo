package togos.schemaschemademo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import togos.schemaschema.namespaces.Core;
import togos.schemaschemademo.XMLEmitter.Attribute;

public class RDFXMLEmitter
{
	public static final String RDF_NS = Core.RDF_NS.prefix;
	
	protected final XMLEmitter xe;
	/** Map of short name (thing before colon) -> long name (usually a URL prefix) */
	protected final Map<String,String> namepacePrefixes;
	protected int xmlnsEmitted = -1;
	int indent = 0;
	boolean autoFormat = true;
	
	public RDFXMLEmitter( XMLEmitter xe, Map<String,String> namespacePrefixes ) {
		this.xe = xe;
		this.namepacePrefixes = namespacePrefixes;
	}
	
	static final Pattern NICE = Pattern.compile("^[a-z0-9_\\-]+$", Pattern.CASE_INSENSITIVE);
	
	protected String abbreviate(String uri) {
		assert uri != null;
		for( Map.Entry<String,String> e : namepacePrefixes.entrySet() ) {
			String prefix = e.getValue();
			if( !uri.startsWith(prefix) ) continue;
			String postfix = uri.substring(prefix.length());
			if( !NICE.matcher(postfix).matches() ) continue;
			return e.getKey()+":"+postfix;
		}
		throw new RuntimeException("No matching prefix abbreviation for "+uri);
	}
	
	protected void open(String tagUri, List<Attribute> attrs) throws IOException {
		String tagName = abbreviate(tagUri);
		ArrayList<Attribute> xmlAttrs = new ArrayList<Attribute>();
		if( xmlnsEmitted < 0 ) {
			for( Map.Entry<String,String> e : namepacePrefixes.entrySet() ) {
				xmlAttrs.add(new Attribute("xmlns:"+e.getKey(), e.getValue()));
			}
		}
		for( Attribute ra : attrs ) {
			xmlAttrs.add(new Attribute(abbreviate(ra.name), ra.value));
		}
		if( autoFormat && indent > 0 ) {
			xe.text("\n");
			for( int i=0; i<indent; ++i ) xe.text("\t");
		}
		xe.open(tagName, xmlAttrs, false);
		++xmlnsEmitted;
		++indent;
	}
	
	protected void close() throws IOException {
		--indent;
		if( autoFormat && xe.state != XMLEmitter.State.OPENING ) {
			xe.text("\n");
			for( int i=0; i<indent; ++i ) xe.text("\t");
		}
		xe.close();
		--xmlnsEmitted;
	}
	
	public void openRdf() throws IOException {
		open(RDF_NS+"RDF", XMLEmitter.attrList());
	}
	
	public void openObject(String typeUri, String url) throws IOException {
		open(typeUri,
			url == null ? XMLEmitter.attrList() :
			url.startsWith("#") ? XMLEmitter.attrList(RDF_NS+"ID", url.substring(1)) :
			XMLEmitter.attrList(RDF_NS+"about", url));
	}
	
	public void openAttribute(String predicateUri) throws IOException {
		open(predicateUri, XMLEmitter.attrList());
	}
	
	public void textAttribute(String predicateUri, String text) throws IOException {
		open(predicateUri, XMLEmitter.attrList());
		xe.text(text);
		autoFormat = false;
		close();
		autoFormat = true;
	}
	
	public void refAttribute(String predicateUri, String uri) throws IOException {
		open(predicateUri, XMLEmitter.attrList(RDF_NS+"resource", uri));
		close();
	}
}
