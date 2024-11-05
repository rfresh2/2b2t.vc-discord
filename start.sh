./mvnw clean package spring-boot:repackage -DskipTests
java -Xmx250m -jar target/2b2t.vc-discord-1.0.jar
