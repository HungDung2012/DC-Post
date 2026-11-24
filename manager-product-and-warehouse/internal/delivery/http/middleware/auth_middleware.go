package middleware

import (
	"errors"
	"math"
	"slices"
	"strings"

	"dc-post/manager-product-and-warehouse/internal/usecase"
	"dc-post/manager-product-and-warehouse/pkg/apperror"
	"dc-post/manager-product-and-warehouse/pkg/response"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

type actorClaims struct {
	UserID int    `json:"user_id"`
	Email  string `json:"email"`
	Role   string `json:"role"`
	jwt.RegisteredClaims
}

func JWTAuth(secret string) gin.HandlerFunc {
	key := []byte(secret)
	parser := jwt.NewParser(jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}), jwt.WithExpirationRequired())
	return func(c *gin.Context) {
		fields := strings.Fields(c.GetHeader("Authorization"))
		if len(fields) != 2 || !strings.EqualFold(fields[0], "Bearer") || len(key) == 0 {
			c.Abort()
			response.JSONError(c, apperror.ErrUnauthorized)
			return
		}

		var claims actorClaims
		token, err := parser.ParseWithClaims(fields[1], &claims, func(token *jwt.Token) (any, error) {
			if token.Method != jwt.SigningMethodHS256 {
				return nil, errors.New("unexpected JWT signing method")
			}
			return key, nil
		})
		if err != nil || !token.Valid || claims.UserID <= 0 || claims.UserID > math.MaxInt32 ||
			strings.TrimSpace(claims.Email) == "" || strings.TrimSpace(claims.Role) == "" {
			c.Abort()
			response.JSONError(c, apperror.ErrUnauthorized)
			return
		}

		c.Set("user_id", claims.UserID)
		c.Set("email", claims.Email)
		c.Set("role", claims.Role)
		c.Next()
	}
}

func ActorFromContext(c *gin.Context) usecase.Actor {
	return usecase.Actor{UserID: c.GetInt("user_id"), Role: c.GetString("role")}
}

func RequireRoles(roles ...string) gin.HandlerFunc {
	return func(c *gin.Context) {
		actor := ActorFromContext(c)
		if actor.UserID <= 0 || !slices.Contains(roles, actor.Role) {
			c.Abort()
			response.JSONError(c, apperror.ErrForbidden)
			return
		}
		c.Next()
	}
}
