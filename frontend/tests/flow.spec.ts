import { test, expect } from '@playwright/test';

test.describe('Retail Banking Platform E2E Flow', () => {

  test('Complete user journey: Login, Open Accounts, and Transfer', async ({ page }) => {
    test.setTimeout(300_000);

    // 1. Visit the frontend
    await page.goto('http://localhost:3000');

    // 2. Login via Keycloak
    page.on('dialog', dialog => console.log(`[DIALOG] ${dialog.message()}`));
    await page.getByRole('button', { name: 'Log In' }).click();
    await page.waitForSelector('#username');
    await page.fill('#username', 'egan');
    await page.fill('#password', 'password123');
    await page.click('input[type="submit"], button[type="submit"]');

    // Handle optional "Update Account Information" screen
    try {
      await page.waitForSelector('#email', { timeout: 3000 });
      await page.fill('#email', 'egan@example.com');
      await page.fill('#firstName', 'Egan');
      await page.fill('#lastName', 'Nguyen');
      await page.click('input[type="submit"], button[type="submit"]');
    } catch (e) {
      // Not required this run
    }

    // 3. Verify Dashboard is fully hydrated (not just URL-matched)
    await page.waitForURL('http://localhost:3000/dashboard');
    await expect(page.locator('h2'))
      .toContainText(/Financial Overview/i, { timeout: 15_000 });

    // 4. Open Checking Account
    await page.getByRole('button', { name: /open.*account/i }).first().click();
    await page.waitForURL('http://localhost:3000/accounts/new');
    await page.click('text=Checking');
    await page.selectOption('select#currency', 'USD');
    await page.selectOption('select#productCode', 'STANDARD');
    await page.click('button:has-text("Create Account")');

    await expect(page.locator('h2'))
      .toContainText('Account Opened Successfully!', { timeout: 15_000 });
    const checkingAccountId = await page
      .locator('span.font-mono.text-\\[var\\(--primary\\)\\]').innerText();

    // 5. Open Savings Account
    await page.click('text=Go to Dashboard');
    await page.waitForURL('http://localhost:3000/dashboard');
    // Wait for full hydration before triggering next navigation
    await expect(page.locator('h2'))
      .toContainText(/Financial Overview/i, { timeout: 15_000 });

    await page.getByRole('button', { name: /open.*account/i }).first().click();
    await page.waitForURL('http://localhost:3000/accounts/new');
    await page.click('text=Savings');
    await page.selectOption('select#currency', 'USD');
    await page.selectOption('select#productCode', 'PREMIUM');
    await page.click('button:has-text("Create Account")');

    await expect(page.locator('h2'))
      .toContainText('Account Opened Successfully!', { timeout: 15_000 });
    const savingsAccountId = await page
      .locator('span.font-mono.text-\\[var\\(--primary\\)\\]').innerText();

    await page.click('text=Go to Dashboard');
    await page.waitForURL('http://localhost:3000/dashboard');

    // 6. Wait for both accounts to appear in the read model projection.
    //    Strategy: wait for the heading first (proves auth is settled),
    //    then poll for account visibility using waitForSelector instead of
    //    page.reload() — avoids the Keycloak re-init race entirely.
    await expect(page.locator('h2'))
      .toContainText(/Financial Overview/i, { timeout: 15_000 });

    for (const [label, id] of [
      ['Checking', checkingAccountId],
      ['Savings',  savingsAccountId],
    ] as const) {
      const shortId = id.split('-')[0];

      let found = false;
      for (let i = 0; i < 15; i++) {
        // Safe reload: trigger refetch but wait for hydration to avoid stalling on init screen
        if (i > 0) {
          await page.reload();
          await expect(page.locator('h2'))
            .toContainText(/Financial Overview/i, { timeout: 15_000 });
        }

        const isPresent = await page.evaluate((sid) => document.body.innerText.includes(sid), shortId);
        if (isPresent) {
          found = true;
          break;
        }
        await page.waitForTimeout(3000);
      }
      if (!found) throw new Error(`[FAIL] ${label} account ${shortId} never appeared on dashboard`);
      console.log(`[OK] ${label} account ${shortId} visible on dashboard`);

      // Manual Deposit for E2E testing: Give the Checking account some money!
      if (label === 'Checking') {
        const { execSync } = require('child_process');

        // Step 1: Wait for backend projection worker to create the balance row.
        //         The worker processes the AccountOpenedEvent asynchronously via Kafka.
        //         We poll until the row exists to avoid our UPDATE being a no-op or
        //         being overwritten by a later INSERT from the projection worker.
        let rowExists = false;
        for (let attempt = 0; attempt < 20; attempt++) {
          try {
            const result = execSync(
              `docker exec postgres psql -U banking -d banking_read -t -A -c "SELECT count(*) FROM balance_projection WHERE account_id = '${id}';"`,
              { encoding: 'utf-8' }
            ).trim();
            if (parseInt(result) > 0) {
              rowExists = true;
              break;
            }
          } catch (e) { /* ignore */ }
          await page.waitForTimeout(1000);
        }
        if (!rowExists) throw new Error(`[FAIL] balance_projection row for ${shortId} never appeared`);

        // Step 2: Give the projection a moment to finish any in-flight writes,
        //         then force the balance to $1000.
        await page.waitForTimeout(2000);
        try {
          execSync(`docker exec postgres psql -U banking -d banking_read -c "UPDATE balance_projection SET available_balance = 1000.00 WHERE account_id = '${id}';"`);
          // Invalidate the Redis balance cache so the API reads from Postgres
          execSync(`docker exec redis redis-cli del "balance:${id}"`);
          console.log(`[OK] Injected $1000.00 into Checking account ${shortId}`);
        } catch (e) {
          console.error(`[WARN] Failed to inject balance via SQL. Test might fail on transfer.`, e);
        }

        // Step 3: Verify via the backend API that the balance is actually $1000
        let verified = false;
        for (let attempt = 0; attempt < 5; attempt++) {
          try {
            const balance = execSync(
              `docker exec postgres psql -U banking -d banking_read -t -A -c "SELECT available_balance FROM balance_projection WHERE account_id = '${id}';"`,
              { encoding: 'utf-8' }
            ).trim();
            if (parseFloat(balance) >= 1000) {
              verified = true;
              break;
            }
          } catch (e) { /* ignore */ }
          // If the projection worker overwrote us, re-inject
          try {
            execSync(`docker exec postgres psql -U banking -d banking_read -c "UPDATE balance_projection SET available_balance = 1000.00 WHERE account_id = '${id}';"`);
            execSync(`docker exec redis redis-cli del "balance:${id}"`);
          } catch (e) { /* ignore */ }
          await page.waitForTimeout(1000);
        }
        if (!verified) console.warn(`[WARN] Could not verify $1000 balance for ${shortId} — transfer may fail`);
      }
    }

    // 7. Navigate to Transfer page
    page.on('response', async response => {
      if (response.url().includes('/transfer') || response.url().includes('/payment') || response.url().includes('/balance')) {
        console.log(`[TRANSFER RESPONSE] ${response.status()} ${response.url()}`);
        try {
          const body = await response.text();
          if (body) console.log('[TRANSFER BODY]', body.slice(0, 500));
        } catch (e) {
          // Response body might not be available (e.g. 204, 304, or redirect)
        }
      }
    });

    await page.getByRole('link', { name: /transfer/i }).click();
    await page.waitForURL('http://localhost:3000/transfers');

    await page.selectOption('select#sourceAccount', checkingAccountId);

    // Force React Query to refetch balance by reloading the page and re-selecting
    await page.reload();
    await page.waitForURL('http://localhost:3000/transfers');
    await page.selectOption('select#sourceAccount', checkingAccountId);

    // Wait for the injected balance to reflect on the transfer page
    await expect(page.locator('text=Available Balance:')).toContainText('1,000', { timeout: 30_000 });

    await page.fill('input[placeholder="Enter exact account ID dashboard..."]', savingsAccountId);
    await page.fill('input[placeholder="e.g. John Doe or Jane Smith"]', 'Egan Nguyen');
    await page.fill('input[placeholder="0.00"]', '50.00');
    await page.fill('input[placeholder="e.g. Rent payment"]', 'E2E Test Transfer');

    await page.click('button:has-text("Send Money")');

    // 8. Verify Transfer Receipt
    await page.waitForURL(/\/transfers\/.+/, { timeout: 60_000 });
    
    // Wait for the status to transition from Pending to Successful if needed
    const statusHeading = page.locator('h2');
    await expect(statusHeading).toBeVisible({ timeout: 15_000 });
    await expect(statusHeading).toContainText(/Transfer Success/i, { timeout: 30_000 });
    
    await expect(page.locator('text=$50.00')).toBeVisible();
    await expect(page.locator(`text=${savingsAccountId}`)).toBeVisible();
  });

});
