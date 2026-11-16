/**
 * Talkflow Cloud Functions.
 *
 * notifyNewMessage: when a message is written to chats/{chatId}/messages, sends a data push
 * to every device of the other chat member. The Android app (TalkflowMessagingService)
 * decides how to show it, based on the user's Notifications and Sounds settings.
 *
 * Deploy: firebase deploy --only functions   (needs the Blaze plan)
 */
const {setGlobalOptions} = require("firebase-functions/v2");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();
// Must sit inside the Firestore database's location (eur3 multi-region → europe-west1).
setGlobalOptions({region: "europe-west1", maxInstances: 10});

// Errors meaning a token will never work again, so it can be removed.
const DEAD_TOKEN_ERRORS = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

exports.notifyNewMessage = onDocumentCreated(
    "chats/{chatId}/messages/{messageId}",
    async (event) => {
      const message = event.data && event.data.data();
      if (!message) return;
      const {chatId} = event.params;
      const db = getFirestore();

      const chatSnap = await db.doc(`chats/${chatId}`).get();
      const chat = chatSnap.data();
      if (!chat) return;

      const senderName = (chat.names && chat.names[message.senderId]) || "Talkflow";
      const recipients = (chat.members || []).filter((uid) => uid !== message.senderId);

      await Promise.all(recipients.map(async (uid) => {
        const tokensRef = db.doc(`fcmTokens/${uid}`);
        const tokens = ((await tokensRef.get()).data() || {}).tokens || [];
        if (tokens.length === 0) return;

        const response = await getMessaging().sendEachForMulticast({
          tokens,
          // Data-only, so the app can apply the user's preview/sound/vibrate settings.
          data: {
            chatId,
            recipientId: uid,
            senderId: String(message.senderId),
            senderName: String(senderName),
            text: String(message.text || "").slice(0, 1000),
          },
          android: {priority: "high"},
        });

        const dead = tokens.filter((token, i) => {
          const result = response.responses[i];
          return !result.success && result.error && DEAD_TOKEN_ERRORS.has(result.error.code);
        });
        if (dead.length > 0) {
          await tokensRef.update({tokens: FieldValue.arrayRemove(...dead)});
        }
        logger.info("notifyNewMessage", {
          chatId, recipient: uid, sent: response.successCount, removedTokens: dead.length,
        });
      }));
    });
