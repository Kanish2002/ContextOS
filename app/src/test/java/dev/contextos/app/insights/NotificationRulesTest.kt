package dev.contextos.app.insights

import org.junit.Assert.*
import org.junit.Test

class NotificationRulesTest {
 @Test fun acrossMidnightQuietHours() {
   assertTrue(NotificationRules.isQuiet(23,22,8))
   assertTrue(NotificationRules.isQuiet(5,22,8))
   assertFalse(NotificationRules.isQuiet(14,22,8))
 }
 @Test fun sameTimeIsAllDayQuiet() {
   assertTrue(NotificationRules.isQuiet(11,8,8))
 }
 @Test fun dailyLimitAndCooldown() {
   assertFalse(NotificationRules.canNotify(12,22,8,1,1,Long.MAX_VALUE))
   assertFalse(NotificationRules.canNotify(12,22,8,0,0,Long.MAX_VALUE))
   assertFalse(NotificationRules.canNotify(12,22,8,0,3,1000))
   assertTrue(NotificationRules.canNotify(12,22,8,0,3,NotificationRules.COOLDOWN_MS))
 }
}
