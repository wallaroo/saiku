#!/bin/bash
#export PATH=/usr/share/java/maven-2.2.1/bin/:$PATH
git merge master
curl https://raw.github.com/pentaho/mondrian/lagunitas/demo/FoodMart.mondrian.xml > util/FoodMart.xml
cp util/FoodMart.xml saiku-core/saiku-service/src/test/resources/org/saiku/olap/discover/FoodMart.xml
cd saiku-core
mvn -U clean install -DskipTests=true 
cd ..
cd saiku-webapp
mvn -U clean install
cd ..
git submodule init
git submodule update
cd saiku-ui
git checkout master 
git pull
mvn -U clean package install:install-file -Dfile=target/saiku-ui-2.6-SNAPSHOT.war  -DgroupId=org.saiku -DartifactId=saiku-ui -Dversion=MONDRIAN4-SNAPSHOT -Dpackaging=war
cd ../saiku-server
mvn -U clean package
cd ../saiku-bi-platform-plugin
mvn -U clean package
cd ../saiku-bi-platform-plugin-p5
mvn clean package
