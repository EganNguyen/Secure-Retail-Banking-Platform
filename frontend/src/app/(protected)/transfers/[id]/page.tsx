"use client";

import { useQuery } from "@tanstack/react-query";
import { transfersApi } from "@/api/transfers";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { CheckCircle2, XCircle, Clock, ArrowRight, Printer } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";

export default function TransferReceiptPage() {
  const params = useParams();
  const transferId = params.id as string;

  const { data: transfer, isLoading } = useQuery({
    queryKey: ['transfer', transferId],
    queryFn: () => transfersApi.getTransfer(transferId),
    refetchInterval: (query) => {
      // Keep polling if it's PENDING
      return query.state.data?.status === 'PENDING' ? 2000 : false;
    }
  });

  if (isLoading) {
    return (
      <div className="max-w-2xl mx-auto mt-10 space-y-4">
        <div className="h-40 w-full bg-[var(--secondary)] rounded-xl animate-pulse" />
        <div className="h-64 w-full bg-[var(--secondary)] rounded-xl animate-pulse" />
      </div>
    );
  }

  if (!transfer) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold">Transfer not found</h2>
        <Link href="/transfers">
          <Button className="mt-4">Make a Transfer</Button>
        </Link>
      </div>
    );
  }

  const isSuccess = transfer.status === 'COMPLETED';
  const isPending = transfer.status === 'PENDING';
  const isFailed = transfer.status === 'FAILED';

  return (
    <div className="max-w-2xl mx-auto space-y-6 pb-20">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold tracking-tight">Transfer Receipt</h1>
        <Button variant="outline" size="sm" onClick={() => window.print()} className="print:hidden">
          <Printer className="mr-2 h-4 w-4" /> Print
        </Button>
      </div>

      <Card className="glass overflow-hidden relative shadow-lg">
        {/* Status Header Banner */}
        <div className={`py-6 flex flex-col items-center justify-center text-white ${
          isSuccess ? 'bg-[var(--success)]' : 
          isFailed ? 'bg-[var(--danger)]' : 
          'bg-[var(--warning)] text-white'
        }`}>
          {isSuccess ? <CheckCircle2 size={48} className="mb-2" /> : 
           isFailed ? <XCircle size={48} className="mb-2" /> : 
           <Clock size={48} className="mb-2 animate-pulse" />}
          
          <h2 className="text-2xl font-bold">
            {isSuccess ? 'Transfer Successful' : 
             isFailed ? 'Transfer Failed' : 
             'Transfer Pending'}
          </h2>
          <p className="opacity-90 mt-1 font-mono text-sm">{transfer.transferId}</p>
        </div>

        <CardContent className="p-8 space-y-8">
          {/* Amount Display */}
          <div className="text-center">
            <h3 className="text-sm uppercase tracking-wider opacity-70 font-medium">Amount Sent</h3>
            <div className="text-5xl font-bold mt-2">
              {new Intl.NumberFormat('en-US', { style: 'currency', currency: transfer.currency }).format(transfer.amount)}
            </div>
          </div>

          <div className="h-px w-full bg-[var(--border)]" />

          {/* Account Details */}
          <div className="grid grid-cols-1 md:grid-cols-5 gap-4 items-center">
            <div className="col-span-2 space-y-1">
              <p className="text-xs uppercase tracking-wider opacity-60">From</p>
              <p className="font-mono text-sm break-all bg-[var(--secondary)] p-2 rounded">{transfer.sourceAccountId}</p>
            </div>
            
            <div className="col-span-1 flex justify-center text-[var(--primary)] py-4 md:py-0">
              <ArrowRight size={24} className="hidden md:block" />
              <ArrowRight size={24} className="rotate-90 md:hidden" />
            </div>
            
            <div className="col-span-2 space-y-1 text-right md:text-left">
              <p className="text-xs uppercase tracking-wider opacity-60">To (Beneficiary)</p>
              <p className="font-medium text-lg">{transfer.beneficiaryName}</p>
              <p className="font-mono text-sm break-all bg-[var(--secondary)] p-2 rounded inline-block md:block">{transfer.destinationAccountId}</p>
            </div>
          </div>

          <div className="h-px w-full bg-[var(--border)]" />

          {/* Metadata */}
          <div className="grid grid-cols-2 gap-y-6 text-sm">
            <div>
              <p className="opacity-60 mb-1">Date</p>
              <p className="font-medium">{new Date(transfer.createdAt).toLocaleString()}</p>
            </div>
            <div>
              <p className="opacity-60 mb-1">Reference</p>
              <p className="font-medium">{transfer.reference || 'None'}</p>
            </div>
            
            {transfer.ledgerReference && (
              <div className="col-span-2">
                <p className="opacity-60 mb-1">Ledger Entry Reference</p>
                <p className="font-mono bg-[var(--secondary)]/50 p-2 rounded block">{transfer.ledgerReference}</p>
              </div>
            )}

            {isFailed && (
              <div className="col-span-2 p-4 bg-[var(--danger)]/10 border border-[var(--danger)]/20 rounded-lg">
                <p className="text-[var(--danger)] font-medium mb-1">Rejection Reason: {transfer.failureReason}</p>
                <p className="text-[var(--danger)]/80 text-xs">{transfer.failureDetail}</p>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-center pt-6 print:hidden">
        <div className="flex gap-4">
          <Link href="/dashboard">
            <Button variant="outline">Back to Dashboard</Button>
          </Link>
          <Link href="/transfers">
            <Button>Make Another Transfer</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
