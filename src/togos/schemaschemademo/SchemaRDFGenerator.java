package togos.schemaschemademo;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import togos.asyncstream.StreamDestination;
import togos.codeemitter.WordUtil;
import togos.schemaschema.Predicate;
import togos.schemaschema.PropertyUtil;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.namespaces.Core;

public class SchemaRDFGenerator implements StreamDestination<SchemaObject, Exception>
{
	protected final RDFXMLEmitter re;
	public SchemaRDFGenerator(Appendable dest, Map<String,String> namespacePrefixes) {
		this.re = new RDFXMLEmitter(new XMLEmitter(dest), namespacePrefixes);
	}

	boolean opened = false;
	
	protected void ensureOpened() throws IOException {
		if( !opened ) {
			re.openRdf();
			opened = true;
		}
	}
	
	protected HashMap<SchemaObject,String> generatedRefs = new HashMap<SchemaObject,String>();
	protected HashSet<String> usedRefs = new HashSet<String>();
	
	protected String maybeGetRef(SchemaObject obj) {
		String ref = obj.getLongName();
		if( ref == null ) ref = generatedRefs.get(obj);
		return ref;
	}
	
	protected String getRef(SchemaObject obj) {
		String ref = obj.getLongName();
		if( ref == null ) ref = generatedRefs.get(obj);
		if( ref == null ) {
			throw new RuntimeException("'"+obj.getName()+"' has no long name");
		}
		return ref;
	}
	
	protected String getOrGenerateRef(SchemaObject obj) {
		String ref = generatedRefs.get(obj);
		if( ref == null ) {
			ref = obj.getLongName();
			if( ref == null ) {
				ref = obj.getName() == null ? "#x" :
					obj instanceof Predicate ? "#"+WordUtil.toCamelCase(obj.getName()) :
						"#"+WordUtil.toPascalCase(obj.getName());
				if( usedRefs.contains(ref) ) { 
					String pfx = ref;
					int d = 2;
					while( usedRefs.contains(ref = pfx+d) ) ++d;
				}
			}
			usedRefs.add(ref);
			generatedRefs.put(obj, ref);
		}
		return ref;
	}

	protected void emitAttribute(Predicate pred, SchemaObject obj) throws IOException {
		if( obj.hasScalarValue() ) {
			re.textAttribute(getRef(pred), obj.getScalarValue().toString());
		} else {
			String ref = maybeGetRef(obj);
			if( ref != null ) {
				re.refAttribute(getRef(pred), ref);
			} else {
				re.openAttribute(getRef(pred));
				emitPrimary(obj);
				re.close();
			}
		}
	}
	
	protected void emitPrimary(SchemaObject obj) throws IOException {
		// If an object is completely blank, don't emit it!
		// Otherwise we'd end up emitting blank Descriptions for aliases.
		if( obj.getProperties().isEmpty() && obj.getLongName() == null && obj.getName() == null ) return;
		
		SchemaObject type = PropertyUtil.getFirstInheritedValue(obj, Core.TYPE, null);
		re.openObject(type == null ? RDFXMLEmitter.RDF_NS+"Description" : getRef(type), getOrGenerateRef(obj));
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
	
	@Override public void data(SchemaObject value) throws IOException {
		ensureOpened();
		emitPrimary(value);
	}

	@Override public void end() throws IOException {
		ensureOpened();
		re.close();
	}

}
