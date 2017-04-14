package imperativestreamer


sealed trait Request
case class ContentsReq(ref: FileRef) extends Request
case class NeighborsReq(ref: VideoRef) extends Request  // Can we use T in place of VideoRef?

sealed trait Response
case class ContentsResp(contents: List[FileRef]) extends Response
case class NeighborsResp(parent: ParentRef, left: FileRef, right: FileRef) extends Response  // Probably we need dependent types here to infer `left` and `right` types
