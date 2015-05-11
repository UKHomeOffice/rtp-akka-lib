publishTo := Some("Artifactory Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

// Enable publishing the jar produced by `test:package`
publishArtifact in (Test, packageBin) := true

// Enable publishing the test API jar
publishArtifact in (Test, packageDoc) := true

// Enable publishing the test sources jar
publishArtifact in (Test, packageSrc) := true