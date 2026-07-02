./mvnw clean package spring-boot:repackage -DskipTests
java -Xmx550m -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+PerfDisableSharedMem -XX:+UseCompactObjectHeaders --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -jar target/2b2t.vc-discord-1.0.jar
