package com.korit.clovapi.domain.shop;

import com.jayway.jsonpath.JsonPath;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;
    private long userId;
    private long cheapItemId;
    private long expensiveItemId;
    private long retiredItemId;
    private long skinItemId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "shop-buyer-" + UUID.randomUUID() + "@example.test";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Abcd1234!\","
                                + "\"nickname\":\"Shop Buyer\","
                                + "\"agreements\":{\"service\":true,\"privacy\":true,\"marketing\":false}}"))
                .andExpect(status().isCreated())
                .andReturn();
        token = JsonPath.read(signup.getResponse().getContentAsString(), "$.data.accessToken");
        userId = Long.parseLong(JsonPath.read(signup.getResponse().getContentAsString(), "$.data.user.id"));

        cheapItemId = insertItem("TEST_CHEAP_HAT", "Cheap Hat", "COSTUME", "COMMON", 100, 0, "ACTIVE");
        expensiveItemId = insertItem("TEST_GOLDEN_CROWN", "Golden Crown", "COSTUME", "LEGENDARY", 999_999, 0, "ACTIVE");
        retiredItemId = insertItem("TEST_RETIRED_SKIN", "Retired Skin", "SKIN", "EPIC", 500, 0, "RETIRED");
        skinItemId = insertItem("TEST_ACTIVE_SKIN", "Active Skin", "SKIN", "COMMON", 50, 0, "ACTIVE");
    }

    @AfterEach
    void cleanUp() {
        // user_preferences.equipped_item_id가 아래에서 지울 shop_items를 참조 중일 수 있어 FK 걸리기 전에 먼저 지운다.
        jdbcTemplate.update("DELETE FROM user_preferences WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_inventory_items WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_wallets WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM shop_items WHERE id IN (?, ?, ?, ?)",
                cheapItemId, expensiveItemId, retiredItemId, skinItemId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void walletStartsWithSignupGrantAndCatalogListsSeededItems() throws Exception {
        mockMvc.perform(get("/api/v1/shop/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20000));

        String grantReason = jdbcTemplate.queryForObject(
                "SELECT reason FROM wallet_transactions WHERE user_id = ?", String.class, userId);
        org.junit.jupiter.api.Assertions.assertEquals("SIGNUP_GRANT", grantReason);

        mockMvc.perform(get("/api/v1/shop/items")
                        .header("Authorization", "Bearer " + token)
                        .param("category", "COSTUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + cheapItemId + "')].owned").value(false));
    }

    @Test
    void purchaseDeductsBalanceRecordsLedgerAndMovesItemIntoInventory() throws Exception {
        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", cheapItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.id").value(String.valueOf(cheapItemId)))
                .andExpect(jsonPath("$.data.item.owned").value(true))
                .andExpect(jsonPath("$.data.balance").value(19900));

        mockMvc.perform(get("/api/v1/shop/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(19900));

        mockMvc.perform(get("/api/v1/shop/inventory")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(cheapItemId)));

        Integer paidPrice = jdbcTemplate.queryForObject(
                "SELECT paid_price FROM user_inventory_items WHERE user_id = ? AND item_id = ?",
                Integer.class, userId, cheapItemId);
        org.junit.jupiter.api.Assertions.assertEquals(100, paidPrice);

        Integer purchaseAmount = jdbcTemplate.queryForObject(
                "SELECT amount FROM wallet_transactions WHERE user_id = ? AND reason = 'PURCHASE'",
                Integer.class, userId);
        org.junit.jupiter.api.Assertions.assertEquals(-100, purchaseAmount);
    }

    @Test
    void purchaseFailsWhenAlreadyOwnedTooExpensiveOrRetired() throws Exception {
        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", cheapItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", cheapItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_ALREADY_OWNED"));

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", expensiveItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"));

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", retiredItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_PURCHASABLE"));

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", 9_999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SHOP_ITEM_NOT_FOUND"));
    }

    @Test
    void equipRequiresOwnershipAndCostumeCategoryThenReflectsInPreferences() throws Exception {
        // preferences 조회/수정을 한 번도 안 거친 신규 유저 — user_preferences 행이 아직 없는 상태에서
        // 바로 장착해도 반영돼야 한다(실제로 겪은 버그: UPDATE가 0건 갱신되고도 200을 반환).
        mockMvc.perform(post("/api/v1/shop/items/{itemId}/equip", cheapItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_OWNED"));

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", cheapItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", skinItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/equip", skinItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_EQUIPPABLE"));

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/equip", cheapItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(String.valueOf(cheapItemId)));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equippedItem.itemId").value(String.valueOf(cheapItemId)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/shop/equipped")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equippedItem").doesNotExist());
    }

    private long insertItem(String code, String name, String category, String rarity, long price,
                             int discountRate, String status) {
        jdbcTemplate.update(
                "INSERT INTO shop_items (code, name, category, rarity, price, discount_rate, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                code, name, category, rarity, price, discountRate, status);
        return jdbcTemplate.queryForObject("SELECT id FROM shop_items WHERE code = ?", Long.class, code);
    }
}
