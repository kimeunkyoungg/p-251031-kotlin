package com.back.global.security

import com.back.domain.member.member.entity.Member
import com.back.domain.member.member.service.MemberService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CustomAuthenticationFilter(
    private val memberService: MemberService,
    private val rq: Rq
) : OncePerRequestFilter() {

    private val excludedPaths = setOf(
        "/api/v1/members/join",
        "/api/v1/members/login"
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        logger.debug("CustomAuthenticationFilter called")

        try {
            authenticate(request, response, filterChain)
        } catch (e: ServiceException) {
            val rsData = e.rsData
            response.contentType = "application/json"
            response.status = rsData.statusCode
            response.writer.write(
                """
                {
                    "resultCode": "${rsData.resultCode}",
                    "msg": "${rsData.msg}"
                }
                """.trimIndent()
            )
        }
    }

    private fun authenticate(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI

        // 1️⃣ 인증이 필요 없는 경로 처리
        if (!uri.startsWith("/api/") || uri in excludedPaths) {
            filterChain.doFilter(request, response)
            return
        }

        // 2️⃣ 인증 헤더 및 쿠키에서 토큰 추출
        val headerAuthorization = rq.getHeader("Authorization", "")
        val (apiKey, accessToken) = when {
            headerAuthorization.isNotBlank() -> parseAuthorizationHeader(headerAuthorization)
            else -> rq.getCookieValue("apiKey", "") to rq.getCookieValue("accessToken", "")
        }

        if (apiKey.isBlank() && accessToken.isBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        // 3️⃣ 액세스 토큰 검증 및 멤버 조회
        val member = getMember(apiKey, accessToken)

        // 4️⃣ 액세스 토큰이 만료되어 새로 발급한 경우
        if (accessToken.isNotBlank() && !isTokenValid(accessToken)) {
            val newAccessToken = memberService.genAccessToken(member)
            rq.setCookie("accessToken", newAccessToken)
            rq.setHeader("accessToken", newAccessToken)
        }

        // 5️⃣ Spring Security 컨텍스트 설정
        val user = SecurityUser(
            member.id,
            member.username,
            "",
            member.nickname,
            member.authorities
        )

        val authentication = UsernamePasswordAuthenticationToken(
            user, user.password, user.authorities
        )
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    private fun parseAuthorizationHeader(header: String): Pair<String, String> {
        require(header.startsWith("Bearer ")) {
            "Authorization 헤더가 Bearer 형식이 아닙니다."
        }

        val parts = header.split(" ", limit = 3)
        val apiKey = parts.getOrNull(1).orEmpty()
        val accessToken = parts.getOrNull(2).orEmpty()
        return apiKey to accessToken
    }

    private fun getMember(apiKey: String, accessToken: String): Member {
        memberService.payloadOrNull(accessToken)?.let { payload ->
            val id = payload["id"] as? Long ?: return@let null
            val username = payload["username"] as? String
            val nickname = payload["nickname"] as? String
            return Member(id, username, nickname)
        }

        return memberService.findByApiKey(apiKey)
            .orElseThrow { ServiceException("401-3", "API 키가 유효하지 않습니다.") }
    }

    private fun isTokenValid(accessToken: String): Boolean =
        memberService.payloadOrNull(accessToken) != null
}
