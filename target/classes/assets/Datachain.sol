pragma solidity ^0.4.0;

contract Datachain {
    address public creator = 0xBBEB98414B408ca6bfBfC7707596520E10C14f64;
    address public fundRecipient;
    uint public minimumToPay;
    uint public privacy;
    uint public sumToUs;
    uint public sumToCreator;
    bytes32 public fileID;

    uint public totalPayed;

    struct Contributor
    {
      uint userID;
      address userAddress;
    }

    uint[] contributions;

    event LogDonation(address addr, uint amount, uint currentTotal);

    modifier isCreator() {
        if (msg.sender != creator) throw;
        _;
    }

    function Datachain(bytes32 _fileID, address _fundRecipient, uint _fileSize, uint _privacy) {
        creator = msg.sender;
        fundRecipient = _fundRecipient;
        fileID = _fileID;
        minimumToPay = _fileSize / 2;
        sumToCreator = minimumToPay*6/10;
        sumToUs = minimumToPay - sumToCreator;
        privacy = _privacy;
        GiveCostAndWallet();
    }

    function contribute(uint _userID) payable{
      if (msg.value < minimumToPay) {
        GiveTrueToPay(false);
        throw;
      }

      contributions.push(_userID);
      if (!creator.send(msg.value)) {
        throw;
      }
      LogDonation(msg.sender, msg.value, totalPayed);
      GiveTrueToPay(true);
    }

    function isPayed(uint payerID) {

      if (privacy == 1) {
        GiveTrueToPay(true);
        return;
      }

      if (privacy == 2) {
        uint i = 0;
        bool check = false;
        while (i <= contributions.length) {
          if (payerID == contributions[i]) {
            check = true;
            break;            
          }
          i++;
        }

        if (check) GiveTrueToPay(true); else GiveTrueToPay(false);
      } else {
        GiveTrueToPay(false);
        throw;
      }
    }

    function GiveCostAndWallet() returns(uint, address) {
      return (minimumToPay, fundRecipient);
    }

    function GiveTrueToPay(bool sol) returns (bool) {
      return sol;
    }


}
