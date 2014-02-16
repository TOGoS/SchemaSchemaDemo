tjbuilder = java -jar util/TJBuilder.jar
touch = ${tjbuilder} touch

all: \
	SchemaSchemaDemo.jar \
	create-tables.sql \
	drop-tables.sql \
	schema.php

clean:
	rm -rf bin ext-lib jar-content SchemaSchemaDemo.jar

install-libraries:
	${tjbuilder} update-libraries ext-lib

save-library-versions:
	mkdir -p ext-lib-refs
	cp ext-lib/LanguageUtil/.git/refs/heads/master ext-lib-refs/LanguageUtil
	cp ext-lib/AsyncStream/.git/refs/heads/master ext-lib-refs/AsyncStream
	cp ext-lib/CodeEmitter/.git/refs/heads/master ext-lib-refs/CodeEmitter
	cp ext-lib/SchemaSchema/.git/refs/heads/master ext-lib-refs/SchemaSchema

.PHONY: all clean compile install-libraries save-library-versions .FORCE

src: .FORCE
	${touch} -latest-within src -latest-within ext-lib src

ext-lib:
	$(MAKE) install-libraries

bin: src ext-lib
	rm -rf bin
	mkdir -p bin
	find src ext-lib/*/src -name '*.java' >.java-src.lst
	javac -d bin @.java-src.lst
	${touch} bin

SchemaSchemaDemo.jar: bin
	jar cfe "$@" togos.schemaschemademo.SchemaProcessor -C bin .

#### Demo stuff

create-tables.sql: SchemaSchemaDemo.jar demo-schema.txt
	java -jar SchemaSchemaDemo.jar demo-schema.txt

drop-tables.sql: SchemaSchemaDemo.jar demo-schema.txt
	java -jar SchemaSchemaDemo.jar demo-schema.txt

schema.php: SchemaSchemaDemo.jar demo-schema.txt
	java -jar SchemaSchemaDemo.jar demo-schema.txt
