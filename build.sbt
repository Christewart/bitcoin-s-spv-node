
test in assembly := {}

scalacOptions ++= Seq("-Xmax-classfile-name", "140")

parallelExecution in Test := false


assemblyMergeStrategy in assembly := { 
  case "logback.xml" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
