package togos.schemaschemademo;

import java.io.IOException;
import java.util.HashMap;

import junit.framework.TestCase;

public class RDFXMLEmitterTest extends TestCase
{
	StringBuilder sb;
	XMLEmitter xe;
	RDFXMLEmitter re;
	
	public void setUp() {
		HashMap<String,String> prefixes = new HashMap<String,String>();
		prefixes.put("rdf", RDFXMLEmitter.RDF_NS);
		
		sb = new StringBuilder();
		xe = new XMLEmitter(sb);
		re = new RDFXMLEmitter(xe, prefixes);
	}
	
	public void testEmitEmptyRdfDocument() throws IOException {
		re.openRdf();
		re.close();
		assertEquals("<rdf:RDF xmlns:rdf=\""+RDFXMLEmitter.RDF_NS+"\"/>", sb.toString());
	}
}
