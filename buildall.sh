#!/bin/bash
curl https://raw.github.com/pentaho/mondrian/lagunitas/demo/FoodMart.mondrian.xml > util/FoodMart.xml
cp util/FoodMart.xml saiku-core/saiku-service/src/test/resources/org/saiku/olap/discover/FoodMart.xml
cd saiku-core
mvn clean install -DskipTests=true 
cd ..
cd saiku-webapp
mvn clean install
cd ..
git submodule init
git submodule update
cd saiku-ui
git checkout master 
git pull
mvn clean package install:install-file -Dfile=target/saiku-ui-2.5-SNAPSHOT.war  -DgroupId=org.saiku -DartifactId=saiku-ui -Dversion=MONDRIAN4-SNAPSHOT -Dpackaging=war
cd ../saiku-server
mvn clean package
cd ../saiku-bi-platform-plugin-util
mvn clean install
cd ../saiku-bi-platform-plugin
mvn clean package
