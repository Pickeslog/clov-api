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
    private long unpurchasableItemId;

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

        cheapItemId = insertItem("Cheap Hat", "COSTUME", "COMMON", 100, 0, true);
        expensiveItemId = insertItem("Golden Crown", "COSTUME", "LEGENDARY", 999_999, 0, true);
        unpurchasableItemId = insertItem("Retired Skin", "SKIN", "EPIC", 500, 0, false);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_shop_items WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_wallets WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM shop_items WHERE id IN (?, ?, ?)",
                cheapItemId, expensiveItemId, unpurchasableItemId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void walletStartsWithABalanceAndCatalogListsSeededItems() throws Exception {
        mockMvc.perform(get("/api/v1/shop/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1000));

        mockMvc.perform(get("/api/v1/shop/items")
                        .header("Authorization", "Bearer " + token)
                        .param("category", "COSTUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + cheapItemId + "')].owned").value(java.util.List.of(false)));
    }

    @Test
    void purchaseDeductsBalanceAndMovesItemIntoInventory() throws Exception {
        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", cheapItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.id").value(String.valueOf(cheapItemId)))
                .andExpect(jsonPath("$.data.item.owned").value(true))
                .andExpect(jsonPath("$.data.balance").value(900));

        mockMvc.perform(get("/api/v1/shop/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(900));

        mockMvc.perform(get("/api/v1/shop/inventory")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(cheapItemId)));
    }

    @Test
    void purchaseFailsWhenAlreadyOwnedTooExpensiveOrNotPurchasable() throws Exception {
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

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", unpurchasableItemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_PURCHASABLE"));

        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", 9_999_999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SHOP_ITEM_NOT_FOUND"));
    }

    private long insertItem(String name, String category, String rarity, long price, int discountRate,
                             boolean purchasable) {
        jdbcTemplate.update(
                "INSERT INTO shop_items (name, category, rarity, price, discount_rate, purchasable) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                name, category, rarity, price, discountRate, purchasable);
        return jdbcTemplate.queryForObject("SELECT id FROM shop_items WHERE name = ?", Long.class, name);
    }
}
