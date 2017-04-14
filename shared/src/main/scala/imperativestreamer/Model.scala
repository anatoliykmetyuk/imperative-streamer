package imperativestreamer

// Boilerplate with `path` and `extends`
sealed trait FileRef { val path: String }
case class  DirRef   (path: String) extends FileRef
case class  VideoRef (path: String) extends FileRef
case class  AudioRef (path: String) extends FileRef
case class  ParentRef(path: String) extends FileRef
case class  MiscRef  (path: String) extends FileRef
case object NullRef                 extends FileRef { val path = "" } // ...
