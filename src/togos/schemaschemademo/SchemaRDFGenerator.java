package togos.schemaschemademo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import togos.asyncstream.StreamDestination;
import togos.lang.BaseSourceLocation;
import togos.lang.CompileError;
import togos.schemaschema.Predicate;
import togos.schemaschema.PropertyUtil;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.namespaces.Core;

public class SchemaRDFGenerator implements StreamDestination<SchemaObject, CompileError>
{
	protected final RDFXMLEmitter re;
	public SchemaRDFGenerator(Appendable dest) {
		HashMap<String,String> namespacePrefixes = new HashMap<String,String>();
		namespacePrefixes.put("rdf", RDFXMLEmitter.RDF_NS);
		this.re = new RDFXMLEmitter(new XMLEmitter(dest), namespacePrefixes);
	}

	boolean opened = false;
	
	protected void ensureOpened() throws IOException {
		if( !opened ) {
			re.openRdf();
			opened = true;
		}
	}
	
	protected String getRef(SchemaObject obj) {
		return obj.getLongName();
	}
	
	protected void emitAttribute(Predicate pred, SchemaObject obj) throws IOException {
		if( obj.hasScalarValue() ) {
			re.textAttribute(getRef(pred), obj.getScalarValue().toString());
		}
		String ref = getRef(obj);
		if( ref != null ) {
			re.refAttribute(getRef(pred), ref);
		} else {
			re.openAttribute(getRef(pred));
			emitReference(obj);
			re.close();
		}
	}
	
	protected void emitPrimary(SchemaObject obj) throws IOException {
		SchemaObject type = PropertyUtil.getFirstInheritedValue(obj, Core.TYPE, null);
		re.openObject(type == null ? RDFXMLEmitter.RDF_NS+"Description" : getRef(type), getRef(obj));
		for( Map.Entry<Predicate,Set<SchemaObject>> pe : obj.getProperties().entrySet() ) {
			if( pe.getKey() == Core.TYPE ) continue;
			if( pe.getKey() == Core.LONGNAME ) continue;
			for( SchemaObject pv : pe.getValue() ) {
				emitAttribute(pe.getKey(), pv);
			}
		}
		re.close();
	}
	
	protected void emitReference(SchemaObject obj) throws IOException {
		// todo: emit reference if possible
		emitPrimary(obj);
	}
	
	@Override public void data(SchemaObject value) throws CompileError {
		try {
			ensureOpened();
			emitPrimary(value);
		} catch( IOException e ) {
			throw new CompileError(e, value.getSourceLocation());
		}
	}

	@Override public void end() throws CompileError {
		try {
			ensureOpened();
			re.close();
		} catch( IOException e ) {
			throw new CompileError(e, BaseSourceLocation.NONE);
		}
	}

}
