package com.back.domain.post.comment.dto

import com.back.domain.post.comment.entity.Comment
import java.time.LocalDateTime

@JvmRecord
//코틀린을 자바로 바꿨다가 자바를 바이트 코드로 바꾸라는 의미
//코틀린으로 100프로 다 전환하기 전까지는 해당 어노테이션 두는 게 좋다.
data class CommentDto private constructor(
    val id: Long,
    val createDate: LocalDateTime,
    val modifyDate: LocalDateTime,
    val content: String,
    val authorId: Long,
    val authorName: String,
    val postId: Long
) {
    constructor(comment: Comment) : this(
        comment.id,
        comment.createDate,
        comment.modifyDate,
        comment.content,
        comment.author.id,
        comment.author.name,
        comment.post.id
    )
}
