package vc;

public class LegacyDBMigration {
//    @Test
//    public void migrate() {
//        SpringApplication app = new SpringApplicationBuilder(Application.class)
//            .registerShutdownHook(true)
//            .build();
//        ConfigurableApplicationContext run = app.run();
//        ConfigDatabase newDatabase = run.getBean(ConfigDatabase.class);
//
//        Connection oldDbConnection;
//        try {
//            final Path dbPath = Paths.get("guild-config.db");
//            oldDbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
//        } catch (final Exception e) {
//            throw new RuntimeException(e);
//        }
//        var oldJdbi = Jdbi.create(oldDbConnection);
//        new SQLitePlugin().customizeJdbi(oldJdbi);
//        oldJdbi.registerRowMapper(ConstructorMapper.factory(LiveFeedConfig.class));
//
//        try (var oldDbHandle = oldJdbi.open()) {
//            oldDbHandle.select("SELECT * FROM guild_config")
//                .mapTo(LiveFeedConfig.class)
//                .stream()
//                .forEach(newDatabase::writeLiveFeedConfig);
//        }
//        run.close();
//    }
}
