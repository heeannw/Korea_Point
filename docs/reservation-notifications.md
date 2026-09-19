# Reservation notifications

The shared header displays a bell for signed-in users. The admin topbar includes
the same component. Notifications are stored in `user_notification`; the current
`spring.jpa.hibernate.ddl-auto=update` configuration creates this table and its
user/id and user/read indexes on application restart.

New reservation submissions (including edits submitted for another review),
approval, rejection, free-admission decisions and successful deposit payments
create notifications for the reservation owner. Writes join the reservation
transaction, so an unsuccessful operation does not commit an alert. Repeated
payment callbacks do not create another confirmation notification. Existing
reservations are not backfilled.

The bell checks the unread count every 30 seconds while the page is visible and
on window focus. Opening it loads the latest 20 notifications, with cursor-based
pagination for older entries. Approval notifications include the reviewed
deposit and deadline, and link to the existing payment route. That route checks
the current reservation status before accepting payment.

All `/api/notifications` endpoints require authentication. Reads and updates use
the session user ID; the client cannot select an owner. PATCH endpoints retain
Spring Security CSRF protection. The interface supports the existing five
languages. This is an in-app inbox; it does not request browser push permission.
