mvn clean compile assembly:single
mvn package
cp $(pwd)/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar tibot.jar
docker build -t tibot .
