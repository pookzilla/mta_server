#!/bin/sh
java \
 -Xmx512m \
 -cp ../libs/dko.jar:/Users/kim/.gradle/caches/modules-2/files-2.1/org.hsqldb/hsqldb/2.3.2/970fd7b8f635e2c19305160459649569655b843c/hsqldb-2.3.2.jar \
 org.kered.dko.Main extract-schema \
 --db-type hsql \
 --schemas MTA \
 --url "jdbc:hsqldb:file:/Users/kim/work/transit/mta/data/mta" \
 --out ../schemas.json
 
 # there is a bug in the DKO generator (or in the schema exporter) in that DOUBLE PRECISION types cannot be handle
sed -i "" 's/DOUBLE PRECISION/DOUBLE/g' ../schemas.json
