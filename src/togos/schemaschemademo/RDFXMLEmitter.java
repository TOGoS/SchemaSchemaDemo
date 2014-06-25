package togos.schemaschemademo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import togos.schemaschemademo.XMLEmitter.Attribute;

public class RDFXMLEmitter
{
	public static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	
	protected final XMLEmitter xe;
	/** Map of short name (thing before colon) -> long name (usually a URL prefix) */
	protected final Map<String,String> namepacePrefixes;
	protected int xmlnsEmitted = -1;
	
	public RDFXMLEmitter( XMLEmitter xe, Map<String,String> namespacePrefixes ) {
		this.xe = xe;
		this.namepacePrefixes = namespacePrefixes;
	}
	
	static final Pattern NICE = Pattern.compile("^[a-z0-9_\\-]+$", Pattern.CASE_INSENSITIVE);
	
	protected String abbreviate(String uri) {
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
		xe.open(tagName, xmlAttrs, false);
		++xmlnsEmitted;
	}
	
	protected void close() throws IOException {
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
		close();
	}
	
	public void refAttribute(String predicateUri, String uri) throws IOException {
		open(predicateUri, XMLEmitter.attrList(RDF_NS+"resource", uri));
		close();
	}
}
