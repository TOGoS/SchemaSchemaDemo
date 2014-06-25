package togos.schemaschemademo;

import java.io.IOException;

import junit.framework.TestCase;

public class XMLEmitterTest extends TestCase
{
	StringBuilder sb;
	XMLEmitter emitter;
	
	public void setUp() {
		sb = new StringBuilder();
		emitter = new XMLEmitter(sb);
	}
	
	public void testEmitSingleTag() throws IOException {
		emitter.open("xxx:foobar", XMLEmitter.attrList(), true);
		assertEquals("<xxx:foobar/>", sb.toString());
	}
	
	public void testEmitOpenCloseTag() throws IOException {
		emitter.open("xxx:foobar", XMLEmitter.attrList(), false);
		emitter.text("");
		emitter.close();
		assertEquals("<xxx:foobar></xxx:foobar>", sb.toString());
	}
	
	public void testEmitMoreComplicated() throws IOException {
		emitter.open("xxx:foobar", XMLEmitter.attrList("yyy:cans","yes","zzz:bans","no"), false);
		emitter.text("Hi");
		emitter.open("parts", XMLEmitter.attrList(), false);
		emitter.close();
		emitter.text("Bye");
		emitter.close();
		assertEquals("<xxx:foobar yyy:cans=\"yes\" zzz:bans=\"no\">Hi<parts/>Bye</xxx:foobar>", sb.toString());
	}
	
	public void testEmitStuffThatNeedsEscaping() throws IOException {
		emitter.open("xxx:foobar", XMLEmitter.attrList("escapeThis","<\"&\">"), false);
		emitter.text("<\"&\">");
		emitter.close();
		assertEquals("<xxx:foobar escapeThis=\"&lt;&quot;&amp;&quot;&gt;\">&lt;\"&amp;\"&gt;</xxx:foobar>", sb.toString());
	}
}
