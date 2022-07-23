module Eversity.XodusDB {
    requires kotlin.stdlib;
    requires dnq;
    requires xodus.openAPI;
    requires kotlinx.serialization.json;
    requires org.joda.time;
    requires dnq.open.api;
    requires eversity.shared.api;
    requires xodus.compress;
    provides by.enrollie.providers.DatabaseProviderInterface with by.enrollie.xodus.DatabaseProviderImplementation;
    provides by.enrollie.providers.PluginMetadataInterface with by.enrollie.xodus.PluginMetadata;
}
