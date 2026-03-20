import { test, expect, type Page } from '@playwright/test';
import { execSync } from 'child_process';

test.describe.serial('Retail Banking Platform E2E Flow', () => {
  let page: Page;
  let checkingAccountId: string;
  let savingsAccountId: string;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('1. Login via Keycloak', async () => {
    test.setTimeout(60_000);
    await page.goto('http://localhost:3000');

    page.on('dialog', dialog => console.log(`[DIALOG] ${dialog.message()}`));
    await page.getByRole('button', { name: 'Log In' }).click();
    await page.waitForSelector('#username');
    await page.fill('#username', 'egan');
    await page.fill('#password', 'password123');
    await page.click('input[type="submit"], button[type="submit"]');

    try {
      await page.waitForSelector('#email', { timeout: 3000 });
      await page.fill('#email', 'egan@example.com');
      await page.fill('#firstName', 'Egan');
      await page.fill('#lastName', 'Nguyen');
      await page.click('input[type="submit"], button[type="submit"]');
    } catch (e) {
      // Not required this run
    }

    await page.waitForURL('http://localhost:3000/dashboard');
    await expect(page.locator('h2')).toContainText(/Financial Overview/i, { timeout: 15_000 });
  });

  test('2. Open Checking Account', async () => {
    test.setTimeout(60_000);
    await page.getByRole('button', { name: /open.*account/i }).first().click();
    await page.waitForURL('http://localhost:3000/accounts/new');
    await page.click('text=Checking');
    await page.selectOption('select#currency', 'USD');
    await page.selectOption('select#productCode', 'STANDARD');
    await page.click('button:has-text("Create Account")');

    await expect(page.locator('h2')).toContainText('Account Opened Successfully!', { timeout: 15_000 });
    checkingAccountId = await page.locator('span.font-mono.text-\\[var\\(--primary\\)\\]').innerText();
  });

  test('3. Open Savings Account', async () => {
    test.setTimeout(60_000);
    await page.click('text=Go to Dashboard');
    await page.waitForURL('http://localhost:3000/dashboard');
    await expect(page.locator('h2')).toContainText(/Financial Overview/i, { timeout: 15_000 });

    await page.getByRole('button', { name: /open.*account/i }).first().click();
    await page.waitForURL('http://localhost:3000/accounts/new');
    await page.click('text=Savings');
    await page.selectOption('select#currency', 'USD');
    await page.selectOption('select#productCode', 'PREMIUM');
    await page.click('button:has-text("Create Account")');

    await expect(page.locator('h2')).toContainText('Account Opened Successfully!', { timeout: 15_000 });
    savingsAccountId = await page.locator('span.font-mono.text-\\[var\\(--primary\\)\\]').innerText();
  });

  test('4. Verify both accounts on Dashboard', async () => {
    test.setTimeout(90_000);
    await page.click('text=Go to Dashboard');
    await page.waitForURL('http://localhost:3000/dashboard');
    await expect(page.locator('h2')).toContainText(/Financial Overview/i, { timeout: 15_000 });

    for (const [label, id] of [['Checking', checkingAccountId], ['Savings', savingsAccountId]] as const) {
      const shortId = id.split('-')[0];
      let found = false;
      for (let i = 0; i < 15; i++) {
        if (i > 0) {
          await page.reload();
          await expect(page.locator('h2')).toContainText(/Financial Overview/i, { timeout: 15_000 });
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
    }
  });

  test('5. Inject Balance to Checking Account', async () => {
    test.setTimeout(90_000);
    let rowExists = false;
    for (let attempt = 0; attempt < 20; attempt++) {
      try {
        const result = execSync(
          `docker exec postgres psql -U banking -d banking_read -t -A -c "SELECT count(*) FROM balance_projection WHERE account_id = '${checkingAccountId}';"`,
          { encoding: 'utf-8' }
        ).trim();
        if (parseInt(result) > 0) {
          rowExists = true;
          break;
        }
      } catch (e) { /* ignore */ }
      await page.waitForTimeout(1000);
    }
    if (!rowExists) throw new Error(`[FAIL] balance_projection row for ${checkingAccountId} never appeared`);

    await page.waitForTimeout(2000);
    try {
      execSync(`docker exec postgres psql -U banking -d banking_read -c "UPDATE balance_projection SET available_balance = 1000.00 WHERE account_id = '${checkingAccountId}';"`);
      execSync(`docker exec redis redis-cli del "balance:${checkingAccountId}"`);
      console.log(`[OK] Injected $1000.00 into Checking account`);
    } catch (e) {
      console.error(`[WARN] Failed to inject balance via SQL. Test might fail on transfer.`, e);
    }

    let verified = false;
    for (let attempt = 0; attempt < 5; attempt++) {
      try {
        const balance = execSync(
          `docker exec postgres psql -U banking -d banking_read -t -A -c "SELECT available_balance FROM balance_projection WHERE account_id = '${checkingAccountId}';"`,
          { encoding: 'utf-8' }
        ).trim();
        if (parseFloat(balance) >= 1000) {
          verified = true;
          break;
        }
      } catch (e) { /* ignore */ }
      try {
        execSync(`docker exec postgres psql -U banking -d banking_read -c "UPDATE balance_projection SET available_balance = 1000.00 WHERE account_id = '${checkingAccountId}';"`);
        execSync(`docker exec redis redis-cli del "balance:${checkingAccountId}"`);
      } catch (e) { /* ignore */ }
      await page.waitForTimeout(1000);
    }
    if (!verified) console.warn(`[WARN] Could not verify $1000 balance for checking account`);
  });

  test('6. Perform Transfer', async () => {
    test.setTimeout(90_000);
    page.on('response', async response => {
      if (response.url().includes('/transfer') || response.url().includes('/payment') || response.url().includes('/balance')) {
        console.log(`[TRANSFER RESPONSE] ${response.status()} ${response.url()}`);
        try {
          const body = await response.text();
          if (body) console.log('[TRANSFER BODY]', body.slice(0, 500));
        } catch (e) { }
      }
    });

    await page.getByRole('link', { name: /transfer/i }).first().click();
    await page.waitForURL('http://localhost:3000/transfers');

    await page.selectOption('select#sourceAccount', checkingAccountId);

    await page.reload();
    await page.waitForURL('http://localhost:3000/transfers');
    await page.selectOption('select#sourceAccount', checkingAccountId);

    await expect(page.locator('text=Available Balance:')).toContainText('1,000', { timeout: 30_000 });

    await page.fill('input[placeholder="Enter exact account ID..."]', savingsAccountId);
    await page.fill('input[placeholder="e.g. John Doe"]', 'Egan Nguyen');
    await page.fill('input[placeholder="0.00"]', '50.00');
    await page.fill('input[placeholder="e.g. Rent payment"]', 'E2E Test Transfer');

    await page.click('button:has-text("Submit Transfer")');
  });

  test('7. Verify Transfer Receipt', async () => {
    test.setTimeout(90_000);
    await page.waitForURL(/\/transfers\/.+/, { timeout: 60_000 });

    const statusHeading = page.locator('h2');
    await expect(statusHeading).toBeVisible({ timeout: 15_000 });
    await expect(statusHeading).toContainText(/Transfer Success/i, { timeout: 30_000 });

    await expect(page.locator('text=$50.00')).toBeVisible();
    await expect(page.locator(`text=${savingsAccountId}`)).toBeVisible();
  });
});
