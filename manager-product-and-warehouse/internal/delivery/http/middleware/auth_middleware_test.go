package middleware

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

func TestJWTAuthAndRequireRoles(t *testing.T) {
	const secret = "test-secret"
	now := time.Now()
	for _, tc := range []struct {
		name, role, key string
		method          jwt.SigningMethod
		expires         time.Time
		userID          any
		want            int
		code            string
	}{
		{"missing", "", "", nil, now, nil, 401, "ERR_UNAUTHORIZED"},
		{"invalid signature", "STAFF", "wrong key", jwt.SigningMethodHS256, now.Add(time.Hour), 19, 401, "ERR_UNAUTHORIZED"},
		{"wrong algorithm", "STAFF", secret, jwt.SigningMethodHS384, now.Add(time.Hour), 19, 401, "ERR_UNAUTHORIZED"},
		{"expired", "STAFF", secret, jwt.SigningMethodHS256, now.Add(-time.Minute), 19, 401, "ERR_UNAUTHORIZED"},
		{"missing expiration", "STAFF", secret, jwt.SigningMethodHS256, time.Time{}, 19, 401, "ERR_UNAUTHORIZED"},
		{"missing role", "", secret, jwt.SigningMethodHS256, now.Add(time.Hour), 19, 401, "ERR_UNAUTHORIZED"},
		{"fractional user", "STAFF", secret, jwt.SigningMethodHS256, now.Add(time.Hour), 1.5, 401, "ERR_UNAUTHORIZED"},
		{"customer forbidden", "CUSTOMER", secret, jwt.SigningMethodHS256, now.Add(time.Hour), 19, 403, "ERR_FORBIDDEN"},
		{"staff allowed", "STAFF", secret, jwt.SigningMethodHS256, now.Add(time.Hour), 19, 200, ""},
	} {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, "/", nil)
			if tc.method != nil {
				claims := jwt.MapClaims{"user_id": tc.userID, "email": "staff@example.test", "role": tc.role}
				if !tc.expires.IsZero() {
					claims["exp"] = tc.expires.Unix()
				}
				token, err := jwt.NewWithClaims(tc.method, claims).SignedString([]byte(tc.key))
				if err != nil {
					t.Fatal(err)
				}
				req.Header.Set("Authorization", "Bearer "+token)
			}
			router := gin.New()
			router.GET("/", JWTAuth(secret), RequireRoles("STAFF", "ADMIN"), func(c *gin.Context) {
				actor := ActorFromContext(c)
				if actor.UserID != 19 || actor.Role != "STAFF" || c.GetString("email") != "staff@example.test" {
					t.Fatalf("incorrect actor: %+v", actor)
				}
				c.Status(http.StatusOK)
			})
			rec := httptest.NewRecorder()
			router.ServeHTTP(rec, req)
			if rec.Code != tc.want || !strings.Contains(rec.Body.String(), tc.code) {
				t.Fatalf("got %d %s, want %d %s", rec.Code, rec.Body, tc.want, tc.code)
			}
		})
	}
}

func TestCORSAndRecovery(t *testing.T) {
	router := gin.New()
	router.Use(RecoveryLogger(), CORS())
	router.GET("/panic", func(*gin.Context) { panic("private panic message") })

	preflight := httptest.NewRequest(http.MethodOptions, "/panic", nil)
	preflight.Header.Set("Origin", "https://client.example.test")
	preflight.Header.Set("Access-Control-Request-Method", "GET")
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, preflight)
	if rec.Code != 204 || rec.Header().Get("Access-Control-Allow-Origin") != "*" ||
		!strings.Contains(rec.Header().Get("Access-Control-Allow-Headers"), "Authorization") {
		t.Fatalf("unexpected CORS response: %d %v", rec.Code, rec.Header())
	}

	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/panic", nil))
	if rec.Code != 500 || !strings.Contains(rec.Body.String(), "ERR_INTERNAL_SERVER") ||
		strings.Contains(rec.Body.String(), "private panic message") {
		t.Fatalf("unexpected panic response: %d %s", rec.Code, rec.Body)
	}
}
