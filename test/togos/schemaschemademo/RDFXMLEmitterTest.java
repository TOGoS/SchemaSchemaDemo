package togos.schemaschemademo;

import java.io.IOException;
import java.util.HashMap;

import junit.framework.TestCase;

public class RDFXMLEmitterTest extends TestCase
{
	StringBuilder sb;
	XMLEmitter xe;
	RDFXMLEmitter re;
	
	static final String JUNK_NS = "http://ns.example.com/Blah/";
	
	static HashMap<String,String> prefixes1 = new HashMap<String,String>();
	static {
		prefixes1.put("rdf", RDFXMLEmitter.RDF_NS);
	}

	static HashMap<String,String> prefixes2 = new HashMap<String,String>();
	static {
		prefixes2.put("rdf", RDFXMLEmitter.RDF_NS);
		prefixes2.put("junk", JUNK_NS);
	}

	public void setUp() {
		sb = new StringBuilder();
		xe = new XMLEmitter(sb);
	}
	
	public void testEmitEmptyRdfDocument() throws IOException {
		re = new RDFXMLEmitter(xe, prefixes1);
		re.openRdf();
		re.close();
		assertEquals("<rdf:RDF xmlns:rdf=\""+RDFXMLEmitter.RDF_NS+"\"/>", sb.toString());
	}
	
	public void testRdfDocumentWithACoupleThings() throws IOException {
		re = new RDFXMLEmitter(xe, prefixes2);
		re.openRdf();
		re.openObject(JUNK_NS+"CornFlake", "#CF1");
		re.textAttribute(JUNK_NS+"crunchiness", "much");
		re.close();
		re.openObject(JUNK_NS+"Bear", "#Winnie");
		re.textAttribute(JUNK_NS+"name", "Winnie the Pooh");
		re.refAttribute(JUNK_NS+"favoriteFlake", "#CF1");
		re.openAttribute(JUNK_NS+"secondFavoriteFlake");
		re.openObject(JUNK_NS+"CornFlake", null);
		re.textAttribute(JUNK_NS+"crunchiness", "very");
		re.close();
		re.close();
		re.close();
		re.close();
		assertEquals(
			"<rdf:RDF xmlns:rdf=\""+RDFXMLEmitter.RDF_NS+"\" xmlns:junk=\""+JUNK_NS+"\">\n"+
			"\t<junk:CornFlake rdf:ID=\"CF1\">\n"+
			"\t\t<junk:crunchiness>much</junk:crunchiness>\n"+
			"\t</junk:CornFlake>\n"+
			"\t<junk:Bear rdf:ID=\"Winnie\">\n"+
			"\t\t<junk:name>Winnie the Pooh</junk:name>\n"+
			"\t\t<junk:favoriteFlake rdf:resource=\"#CF1\"/>\n"+
			"\t\t<junk:secondFavoriteFlake>\n"+
			"\t\t\t<junk:CornFlake>\n"+
			"\t\t\t\t<junk:crunchiness>very</junk:crunchiness>\n"+
			"\t\t\t</junk:CornFlake>\n"+
			"\t\t</junk:secondFavoriteFlake>\n"+
			"\t</junk:Bear>\n"+
			"</rdf:RDF>",
			sb.toString()
		);
	}
}
