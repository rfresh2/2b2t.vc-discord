mvn clean package spring-boot:repackage -DskipTests
java -Xmx100m -jar target/2b2t.vc-discord-1.0.jar
